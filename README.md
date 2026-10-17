# PediuPatrão — Atividade Prática M1

Sistema de pedidos de pizzaria (Java 21, Spring Boot 3.4, Spring Security, MongoDB e Thymeleaf), evoluído com controle de acesso por perfil, fluxo controlado de pedidos, descontos e cancelamentos exclusivos do gerente e auditoria persistente.

## Como executar

Pré-requisitos: **JDK 21** e um MongoDB acessível (ex.: cluster gratuito do MongoDB Atlas).

A string de conexão **não fica no repositório**. Informe-a de uma das formas:

- copie `segredos.properties.exemplo` para `segredos.properties` (na raiz do projeto) e preencha a sua string de conexão; ou
- defina a variável de ambiente `MONGODB_URI`.

Sem nenhuma das duas, a aplicação tenta `mongodb://localhost:27017`. O banco usado é `pediupatrao`.

```bash
# Windows
mvnw.cmd spring-boot:run
# Linux/macOS
./mvnw spring-boot:run
```

Acesse `http://localhost:8080`. Na primeira execução, o `DataInitializer` cria um usuário de cada perfil e alguns produtos, caso ainda não existam:

| Usuário       | Senha      | Perfil    |
|---------------|------------|-----------|
| `admin`       | `teste123` | ADMIN     |
| `gerente01`   | `teste123` | GERENTE   |
| `atendente01` | `teste123` | ATENDENTE |

Testes automatizados:

```bash
mvnw.cmd test
```

## Requisitos implementados

### RF01 — Usuários e permissões

- Perfis `ADMIN`, `GERENTE` e `ATENDENTE` (`entity/Perfil`). O `UsuarioDetailsService` atribui a role a partir do perfil salvo (`ROLE_<PERFIL>`), e não mais `USER` fixo.
- A autorização é feita **no backend**, em duas camadas:
  1. regras por URL em `SecurityConfig` (telas e APIs);
  2. `@PreAuthorize` nos serviços (`@EnableMethodSecurity`), que protege a regra mesmo se um controlador futuro esquecer a regra de URL.
- Permissões:

| Operação | ADMIN | GERENTE | ATENDENTE |
|---|:-:|:-:|:-:|
| Administrar usuários e perfis | ✔ | | |
| Consultar pedidos | ✔ | ✔ | ✔ |
| Registrar pedidos e avançar status | | ✔ | ✔ |
| **Aplicar desconto / cancelar pedido** | | ✔ | |
| Consultar clientes | | ✔ | ✔ |
| Cadastrar e atualizar clientes | | | ✔ |
| Excluir clientes | | ✔ | |
| Consultar auditoria | ✔ | ✔ | |
| Manter produtos | ✔ | ✔ | |

- Proteção contra elevação de privilégio (`UsuarioService`):
  - `/api/usuarios` deixou de ser público (antes qualquer pessoa podia criar usuários);
  - ninguém altera o próprio perfil nem exclui o próprio usuário;
  - o último ADMIN não pode ser rebaixado nem excluído;
  - qualquer `id` enviado na criação é ignorado, então não dá para sobrescrever outro usuário;
  - a senha é gravada com BCrypt e nunca é devolvida pela API (`@JsonProperty(WRITE_ONLY)`).
- **CSRF ativo**: formulários Thymeleaf recebem o token automaticamente; chamadas `fetch` usam `static/js/api.js`, que envia o token no cabeçalho. O logout passou a ser `POST`.
- Acesso negado: as APIs respondem `401` (não autenticado) ou `403` em JSON, e as telas mostram a página de erro 403. Toda tentativa negada é registrada no log e na auditoria (`ACESSO_NEGADO`).

### RF02 — Cadastro de pedidos

- `Pedido` passou a ter itens (`ItemPedido`: produto, quantidade, preço unitário e subtotal), subtotal, percentual e valor de desconto, valor total, tipo (entrega/retirada), responsável e data/hora de entrada, além do histórico de status.
- A requisição (`NovoPedidoRequest`) só aceita cliente, tipo, observação e itens (produto + quantidade). **Preços e total são calculados no servidor** (`Pedido.recalcularValores`) a partir do cadastro de produtos; um `valorTotal` enviado na requisição é ignorado.
- Validações: cliente existente, ao menos um item, quantidade de 1 a 99, produto ativo e com preço, e endereço obrigatório para entrega.

### RF03 — Entrada, saída e transições

```
RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA (entrega) → FINALIZADO
                                  → RETIRADO          (retirada) → FINALIZADO
CANCELADO: só pelo GERENTE, antes da saída, com justificativa
```

- As transições ficam em `StatusPedido` e são validadas no `PedidoService`. Transições inválidas são rejeitadas, por exemplo finalizar um pedido em preparação, marcar `RETIRADO` em pedido de entrega, alterar um pedido cancelado ou cancelar pela rota comum de status.
- São registrados data/hora e responsável da **entrada** (RECEBIDO), da **saída** (SAIU PARA ENTREGA / RETIRADO), da finalização e do cancelamento, além do histórico de cada mudança.

### RF04 — Cancelamentos e descontos

- `POST /api/pedidos/{id}/desconto` e `POST /api/pedidos/{id}/cancelar` (e as ações equivalentes na tela) são exclusivos do GERENTE, inclusive em relação ao ADMIN.
- Desconto: percentual entre 0,01% e **20%**, aplicado somente antes da saída e recalculado no servidor.
- Cancelamento: justificativa obrigatória (mínimo de 10 caracteres), somente antes da saída. Itens e valores são preservados e o pedido não aceita mais alterações.
- As duas operações geram auditoria com usuário, data/hora, valores anteriores e novos e a justificativa ou motivo.

### RF05 — Auditoria

- Entidade `RegistroAuditoria` (coleção `auditoria`), `RegistroAuditoriaRepository` e `AuditoriaService`.
- Cada evento registra o usuário e o perfil, a data/hora, o tipo de operação, a entidade e seu identificador, os **campos alterados com valor anterior e novo** e a justificativa, quando houver.
- Eventos auditados: inclusão de pedido, mudança de status, desconto, cancelamento, inclusão, alteração e exclusão de cliente (endereço e telefone incluídos), operações com usuários e tentativas de acesso negadas.
- Integridade: a entidade é imutável (campos `final`, sem setters), o repositório estende `Repository` (sem métodos de exclusão) e só existe `GET /api/auditoria`. Qualquer outro método nessa rota é negado (`denyAll`).
- Proteção de dados: senhas e tokens nunca são gravados (filtro de campos proibidos no `AuditoriaService`). Telefones são gravados mascarados, com DDD e os 4 últimos dígitos.
- Consulta restrita a ADMIN e GERENTE em `/auditoria`, com filtros por entidade, identificador, usuário e operação. Ela também aparece na tela de detalhes do pedido.

## Principais arquivos alterados ou criados

| Camada | Arquivos |
|---|---|
| Configuração | `config/SecurityConfig`, `config/DataInitializer`, `config/MongoConversoesConfig`, `config/MongoInitConfig` |
| Segurança | `security/UsuarioLogado`, `security/AcessoNegadoHandler`, `service/UsuarioDetailsService` |
| Entidades | `Pedido`, `ItemPedido`, `HistoricoStatus`, `StatusPedido`, `TipoEntrega`, `Perfil`, `Usuario`, `RegistroAuditoria`, `AlteracaoCampo`, `TipoOperacao` |
| Serviços | `PedidoService`, `ClienteService`, `UsuarioService`, `AuditoriaService`, `ComparadorCampos` |
| Controladores | `PedidoController` (API), `PedidoPageController`, `AuditoriaController` (API), `AuditoriaPageController`, `ClienteController`, `UsuarioController`, `HomeController` |
| Erros | `exception/RegraNegocioException`, `RecursoNaoEncontradoException`, `ApiExceptionHandler` |
| Telas | `pedidos.html`, `pedidos/form.html`, `pedidos/detalhe.html`, `auditoria/lista.html`, `fragments/sidebar.html`, `layout.html`, telas de clientes, usuários e produtos |
| Testes | `PedidoServiceTest`, `ClienteServiceTest`, `UsuarioServiceTest`, `SegurancaApiTest`, `PaginasTest` |

## Demonstrando o bloqueio no backend

Mesmo com os botões ocultos, a API recusa a operação. Faça login como `atendente01` (ou `admin`), abra o console do navegador (F12) em qualquer página do sistema e execute:

```js
// troque ID por um id de pedido real (aparece na URL da tela de detalhes)
await api('/api/pedidos/ID/cancelar', { method: 'POST', body: { justificativa: 'Teste de acesso indevido' } })
// → { status: 403, ok: false, dados: { mensagem: "Acesso negado: ..." } }

await api('/api/pedidos/ID/desconto', { method: 'POST', body: { percentual: 50 } })
// → 403 para atendente/admin; como gerente → 400 "O desconto máximo permitido é de 20%..."

await api('/api/usuarios', { method: 'POST', body: { username: 'hacker', password: '123456', role: 'ADMIN' } })
// → 403 (somente ADMIN cria usuários)
```

As tentativas aparecem na auditoria como **"Tentativa de acesso negada"**.

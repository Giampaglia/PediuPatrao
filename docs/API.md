# API do Dashboard — Documentação dos endpoints

Base: `http://localhost:8080/api/dashboard` · Formato: JSON (UTF-8) · Métodos: **somente `GET`**.

Os exemplos de resposta abaixo foram obtidos da aplicação em execução (respostas completas em [`evidencias/api/`](evidencias/api/)).

## Sumário

| Método | Endpoint | Requisito | Descrição |
|---|---|---|---|
| GET | [`/resumo`](#get-resumo) | RF01 | Indicadores gerais e comparativo com o período anterior |
| GET | [`/vendas`](#get-vendas) | RF02 | Série de vendas por dia, semana, mês, hora do dia ou dia da semana |
| GET | [`/vendas/categorias`](#get-vendascategorias) | RF02 | Faturamento por categoria de produto |
| GET | [`/vendas/tipos-entrega`](#get-vendastipos-entrega) | RF02 | Entrega x retirada |
| GET | [`/pedidos/status`](#get-pedidosstatus) | RF03 | Quantidade de pedidos por status |
| GET | [`/pedidos/recentes`](#get-pedidosrecentes) | RF03 | Últimos pedidos do recorte |
| GET | [`/produtos/mais-vendidos`](#get-produtosmais-vendidos) | RF04 | Ranking de produtos |
| GET | [`/filtros`](#get-filtros) | RF05 | Valores aceitos pelos filtros |

---

## Autenticação e permissão

A API usa a **sessão** do sistema (login por formulário do Spring Security). Somente os perfis **ADMIN** e **GERENTE** têm acesso.

| Situação | Resposta |
|---|---|
| Sem sessão | `401 Unauthorized` (sem corpo) |
| Perfil ATENDENTE | `403 Forbidden` (a tentativa é registrada na auditoria) |
| Método diferente de GET | `403 Forbidden` |

No navegador, as chamadas são feitas pelo próprio dashboard. Para testar pelo terminal com `curl`:

```bash
# 1) obtém o token CSRF da tela de login e faz login (guarda o cookie de sessão)
TOKEN=$(curl -s -c cookies.txt http://localhost:8080/login | grep -o 'name="_csrf" value="[^"]*"' | sed 's/.*value="//;s/"$//')
curl -s -b cookies.txt -c cookies.txt -X POST http://localhost:8080/login \
     --data-urlencode username=gerente01 --data-urlencode password=teste123 --data-urlencode _csrf=$TOKEN -o /dev/null

# 2) chama a API com o cookie
curl -s -b cookies.txt "http://localhost:8080/api/dashboard/resumo?dataInicio=2026-11-01&dataFim=2026-11-08"
```

---

## Filtros comuns

Aceitos por **todos** os endpoints, exceto `/filtros`. Todos são opcionais.

| Parâmetro | Tipo | Exemplo | Padrão | Regras |
|---|---|---|---|---|
| `dataInicio` | data `AAAA-MM-DD` | `2026-11-01` | `dataFim` − 29 dias | Inclusiva. Não pode ser posterior a `dataFim`. |
| `dataFim` | data `AAAA-MM-DD` | `2026-11-08` | hoje | Inclusiva. Período máximo de **366 dias**. |
| `status` | lista de status | `status=FINALIZADO&status=CANCELADO` ou `status=FINALIZADO,CANCELADO` | todos | `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `SAIU_PARA_ENTREGA`, `RETIRADO`, `FINALIZADO`, `CANCELADO` (sem diferenciar maiúsculas). |
| `tipoEntrega` | texto | `ENTREGA` | todos | `ENTREGA` ou `RETIRADA`. |
| `categoria` | texto | `Pizza` | todas | Categoria (tipo) do produto, sem diferenciar maiúsculas; até 100 caracteres. |
| `produtoId` | texto | `6af0f0f31679014347f7c247` | todos | Id do produto; até 100 caracteres. |

**Regras de cálculo** (detalhes em [REQUISITOS.md § 4](REQUISITOS.md#4-regras-de-negócio)):
- O período usa a **data de entrada** do pedido.
- Faturamento, ticket médio, itens, séries, ranking e categorias consideram só **vendas válidas** (status diferente de `CANCELADO`).
- Com `categoria` ou `produtoId`, entram os pedidos que contêm o item, mas os valores somam **apenas os itens filtrados** (com o desconto rateado).

**Convenções da resposta:** datas no formato ISO-8601 (`2026-11-08`, `2026-11-08T18:55:09`); valores em reais como número decimal com até 2 casas; percentuais com 1 casa (`33.3` = 33,3%); enums em texto.

---

## Erros

Todos os erros da API (exceto 401) seguem o mesmo formato:

```json
{
  "dataHora": "2026-11-08T19:01:05.501076900",
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "A data inicial (dataInicio) não pode ser posterior à data final (dataFim).",
  "caminho": "/api/dashboard/resumo"
}
```

| HTTP | Quando | Exemplo de `mensagem` |
|---|---|---|
| 400 | `dataInicio` posterior a `dataFim` | `A data inicial (dataInicio) não pode ser posterior à data final (dataFim).` |
| 400 | Data em formato inválido | `Data inválida no parâmetro 'dataInicio': '08/11/2026'. Use o formato AAAA-MM-DD, ex.: 2026-11-08.` |
| 400 | Período maior que 366 dias | `O período consultado não pode ultrapassar 366 dias (informado: 1043 dias).` |
| 400 | Valor de enum inexistente (`status`, `tipoEntrega`, `agrupamento`, `ordenarPor`) | `Valor inválido no parâmetro 'status': 'VOANDO'. Valores aceitos: RECEBIDO, EM_PREPARACAO, PRONTO, SAIU_PARA_ENTREGA, RETIRADO, FINALIZADO, CANCELADO.` |
| 400 | `limite` fora de 1–50 | `O parâmetro 'limite' deve estar entre 1 e 50 (informado: 500).` |
| 400 | `limite` não numérico | `Requisição inválida: valor 'abc' não aceito no parâmetro 'limite'.` |
| 400 | `categoria`/`produtoId` com mais de 100 caracteres | `O parâmetro 'categoria' aceita no máximo 100 caracteres.` |
| 401 | Sem sessão | (sem corpo) |
| 403 | Perfil sem permissão ou método não permitido | `Acesso negado: Perfil sem permissão para a operação.` |
| 503 | MongoDB indisponível (o driver desiste em 5 s) | `Não foi possível consultar o banco de dados no momento. Tente novamente em instantes.` |

---

## GET /resumo

**RF01** — indicadores gerais do período e comparação com o período anterior de mesma duração.

**Parâmetros:** apenas os [filtros comuns](#filtros-comuns).

**Requisição**

```http
GET /api/dashboard/resumo
```

**Resposta `200`**

```json
{
  "periodo": { "dataInicio": "2026-10-10", "dataFim": "2026-11-08", "dias": 30 },
  "faturamento": 38279.23,
  "totalPedidos": 482,
  "pedidosValidos": 450,
  "pedidosCancelados": 32,
  "taxaCancelamento": 6.6,
  "ticketMedio": 85.06,
  "itensVendidos": 1466,
  "totalDescontos": 328.77,
  "pedidosComDesconto": 32,
  "pedidosEmAndamento": 4,
  "tempoMedioAteSaidaMinutos": 34.6,
  "comparativo": {
    "periodoAnterior": { "dataInicio": "2026-09-10", "dataFim": "2026-10-09", "dias": 30 },
    "faturamento": 32864.25,
    "pedidosValidos": 399,
    "ticketMedio": 82.37,
    "variacaoFaturamento": 16.5,
    "variacaoPedidos": 12.8,
    "variacaoTicketMedio": 3.3
  },
  "qualidadeDados": { "pedidosSemStatus": 0, "pedidosSemValor": 0, "pedidosSemDataEntrada": 0 },
  "geradoEm": "2026-11-08T19:00:57.9008307"
}
```

| Campo | Descrição |
|---|---|
| `totalPedidos` | Todos os pedidos do recorte, inclusive cancelados e sem status. |
| `pedidosValidos` | Pedidos não cancelados (base de faturamento e ticket). |
| `taxaCancelamento` | % de cancelados entre os pedidos com status reconhecido. |
| `tempoMedioAteSaidaMinutos` | `null` quando nenhum pedido saiu no período. |
| `comparativo.variacao*` | Variação percentual; `null` quando o período anterior não tem vendas. |
| `qualidadeDados` | Registros incompletos tratados (RF07). `pedidosSemDataEntrada` é o total da base, porque esses pedidos não pertencem a nenhum período. |

**Erros:** 400 (filtros), 401, 403, 503.

---

## GET /vendas

**RF02** — série de vendas válidas agrupada. Todos os pontos do período aparecem, inclusive os sem venda (com zero).

**Parâmetros:** filtros comuns e:

| Parâmetro | Valores | Padrão |
|---|---|---|
| `agrupamento` | `DIA`, `SEMANA` (segunda a domingo), `MES`, `HORA_DO_DIA` (24 pontos), `DIA_DA_SEMANA` (7 pontos, de SEG a DOM) | `DIA` |

**Requisição**

```http
GET /api/dashboard/vendas?agrupamento=DIA&dataInicio=2026-11-02&dataFim=2026-11-08
```

**Resposta `200`** (pontos resumidos)

```json
{
  "agrupamento": "DIA",
  "periodo": { "dataInicio": "2026-11-02", "dataFim": "2026-11-08", "dias": 7 },
  "totalPedidos": 91,
  "faturamentoTotal": 7707.86,
  "pontos": [
    { "chave": "2026-11-02", "rotulo": "02/11", "pedidos": 7,  "faturamento": 561.00,  "ticketMedio": 80.14 },
    { "chave": "2026-11-03", "rotulo": "03/11", "pedidos": 13, "faturamento": 1168.10, "ticketMedio": 89.85 }
  ]
}
```

| Agrupamento | `chave` | `rotulo` |
|---|---|---|
| `DIA` | `2026-11-02` | `02/11` |
| `SEMANA` | data da segunda-feira, `2026-10-26` | `Sem. 26/10` |
| `MES` | `2026-11` | `nov/26` |
| `HORA_DO_DIA` | `0` … `23` | `00h` … `23h` |
| `DIA_DA_SEMANA` | `SEG` … `DOM` | `Seg` … `Dom` |

**Exemplos:** `?agrupamento=HORA_DO_DIA` ([resposta](evidencias/api/vendas-hora.json)) · `?agrupamento=MES&dataInicio=2026-08-11&dataFim=2026-11-08` ([resposta](evidencias/api/vendas-mes.json)).

**Erros:** 400 (`agrupamento` inválido ou filtros), 401, 403, 503.

---

## GET /vendas/categorias

**RF02** — faturamento e itens vendidos por categoria (tipo) do produto, do maior para o menor faturamento.

**Requisição**

```http
GET /api/dashboard/vendas/categorias
```

**Resposta `200`**

```json
{
  "periodo": { "dataInicio": "2026-10-10", "dataFim": "2026-11-08", "dias": 30 },
  "faturamentoTotal": 38279.23,
  "categorias": [
    { "categoria": "Pizza",  "quantidade": 557, "faturamento": 28795.63, "participacao": 75.2 },
    { "categoria": "Esfiha", "quantidade": 601, "faturamento": 4335.92,  "participacao": 11.3 },
    { "categoria": "Lanche", "quantidade": 116, "faturamento": 3007.63,  "participacao": 7.9 },
    { "categoria": "Bebida", "quantidade": 192, "faturamento": 2140.05,  "participacao": 5.6 }
  ]
}
```

`participacao` = % do faturamento total. Itens de produtos excluídos do cadastro aparecem como `"Sem categoria"`.

**Erros:** 400, 401, 403, 503.

---

## GET /vendas/tipos-entrega

**RF02** — vendas válidas por tipo de entrega.

**Requisição**

```http
GET /api/dashboard/vendas/tipos-entrega
```

**Resposta `200`**

```json
{
  "periodo": { "dataInicio": "2026-10-10", "dataFim": "2026-11-08", "dias": 30 },
  "totalPedidos": 450,
  "tipos": [
    { "tipo": "ENTREGA",  "descricao": "Entrega",            "pedidos": 270, "faturamento": 22958.04, "ticketMedio": 85.03, "participacao": 60.0 },
    { "tipo": "RETIRADA", "descricao": "Retirada no balcão", "pedidos": 180, "faturamento": 15321.19, "ticketMedio": 85.12, "participacao": 40.0 }
  ]
}
```

`participacao` = % dos pedidos válidos.

**Erros:** 400, 401, 403, 503.

---

## GET /pedidos/status

**RF03** — quantidade de pedidos por status. Os 7 status sempre aparecem, na ordem do fluxo.

**Requisição**

```http
GET /api/dashboard/pedidos/status?dataInicio=2026-11-08&dataFim=2026-11-08
```

**Resposta `200`** (lista resumida)

```json
{
  "periodo": { "dataInicio": "2026-11-08", "dataFim": "2026-11-08", "dias": 1 },
  "total": 8,
  "emAndamento": 4,
  "finalizados": 3,
  "cancelados": 1,
  "porStatus": [
    { "status": "RECEBIDO",      "descricao": "Recebido",      "quantidade": 0, "percentual": 0.0,  "valorTotal": 0.00 },
    { "status": "EM_PREPARACAO", "descricao": "Em preparação", "quantidade": 4, "percentual": 50.0, "valorTotal": 181.50 }
  ]
}
```

`emAndamento` = Recebido + Em preparação + Pronto + Saiu para entrega + Retirado. `valorTotal` inclui pedidos cancelados (valor que estava em jogo). [Resposta completa](evidencias/api/status.json).

**Erros:** 400, 401, 403, 503.

---

## GET /pedidos/recentes

**RF03** — pedidos do recorte, do mais recente para o mais antigo (ordenação e limite aplicados no banco).

**Parâmetros:** filtros comuns e:

| Parâmetro | Tipo | Padrão | Regras |
|---|---|---|---|
| `limite` | inteiro | `10` | de 1 a 50 |

**Requisição**

```http
GET /api/dashboard/pedidos/recentes?limite=3
```

**Resposta `200`** (um item mostrado)

```json
[
  {
    "id": "6af0f0f41679014347f7c76c",
    "codigo": "F7C76C",
    "clienteNome": "Bruno Costa",
    "status": "EM_PREPARACAO",
    "statusDescricao": "Em preparação",
    "tipoEntrega": "ENTREGA",
    "quantidadeItens": 1,
    "valorTotal": 12.00,
    "dataHoraEntrada": "2026-11-08T18:55:09",
    "minutosDesdeEntrada": 5
  }
]
```

**Erros:** 400 (`limite` fora da faixa ou não numérico), 401, 403, 503.

---

## GET /produtos/mais-vendidos

**RF04** — ranking de produtos das vendas válidas.

**Parâmetros:** filtros comuns e:

| Parâmetro | Valores | Padrão |
|---|---|---|
| `ordenarPor` | `QUANTIDADE` ou `FATURAMENTO` | `QUANTIDADE` |
| `limite` | 1 a 50 | `10` |

Desempate: o outro critério e, depois, o nome do produto.

**Requisição**

```http
GET /api/dashboard/produtos/mais-vendidos?ordenarPor=FATURAMENTO&limite=3
```

**Resposta `200`** (um produto mostrado)

```json
{
  "periodo": { "dataInicio": "2026-10-10", "dataFim": "2026-11-08", "dias": 30 },
  "criterio": "FATURAMENTO",
  "limite": 3,
  "totalItensVendidos": 1466,
  "faturamentoTotal": 38279.23,
  "produtos": [
    {
      "posicao": 1,
      "produtoId": "6af0f0f31679014347f7c247",
      "nome": "Pizza Calabresa",
      "categoria": "Pizza",
      "quantidade": 127,
      "faturamento": 6287.40,
      "pedidos": 107,
      "participacao": 16.4
    }
  ]
}
```

| Campo | Descrição |
|---|---|
| `pedidos` | Em quantos pedidos o produto apareceu. |
| `participacao` | % do total segundo o critério (itens para `QUANTIDADE`, faturamento para `FATURAMENTO`). |
| `totalItensVendidos`, `faturamentoTotal` | Totais de **todos** os produtos, e não só do top N. |

**Erros:** 400 (`ordenarPor`/`limite` inválidos ou filtros), 401, 403, 503.

---

## GET /filtros

**RF05** — valores aceitos pelos filtros, para montar a tela. Não recebe parâmetros.

**Requisição**

```http
GET /api/dashboard/filtros
```

**Resposta `200`** (listas resumidas)

```json
{
  "periodoPadrao": { "dataInicio": "2026-10-10", "dataFim": "2026-11-08", "dias": 30 },
  "limitePeriodoDias": 366,
  "primeiroPedido": "2026-08-11T11:07:36",
  "ultimoPedido": "2026-11-08T18:55:09",
  "status": [
    { "valor": "RECEBIDO", "descricao": "Recebido" },
    { "valor": "EM_PREPARACAO", "descricao": "Em preparação" }
  ],
  "tiposEntrega": [
    { "valor": "ENTREGA", "descricao": "Entrega" },
    { "valor": "RETIRADA", "descricao": "Retirada no balcão" }
  ],
  "categorias": ["Bebida", "Esfiha", "Lanche", "Pizza"],
  "produtos": [
    { "id": "6af0f0f41679014347f7c24d", "nome": "Esfiha de Carne", "categoria": "Esfiha", "ativo": true }
  ],
  "agrupamentos": [
    { "valor": "DIA", "descricao": "Por dia" },
    { "valor": "HORA_DO_DIA", "descricao": "Por hora do dia (horários de pico)" }
  ],
  "criteriosRanking": [
    { "valor": "QUANTIDADE", "descricao": "Quantidade vendida" },
    { "valor": "FATURAMENTO", "descricao": "Faturamento" }
  ]
}
```

`categorias` são deduplicadas sem diferenciar maiúsculas. `primeiroPedido`/`ultimoPedido` são `null` quando não há pedidos.

**Erros:** 401, 403, 503.

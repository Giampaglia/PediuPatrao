# Evidências de Testes — Dashboard Administrativo

Resultado da última execução (`mvnw test`, 08/11/2026): **94 testes, 0 falhas, 0 erros — BUILD SUCCESS.**
Saída completa: [`evidencias/testes/resultado-mvn-test.txt`](evidencias/testes/resultado-mvn-test.txt) · relatórios do Surefire: [`evidencias/testes/surefire/`](evidencias/testes/surefire/).

## 1. Estratégia

| Nível | O que valida | Ferramentas | Banco |
|---|---|---|---|
| Unitário | Regras de cálculo e validação dos filtros | JUnit 5, Mockito | — |
| API (HTTP) | Rotas, permissões, códigos e mensagens de erro, formato do JSON | `@WebMvcTest`, MockMvc, Spring Security Test | simulado |
| Integração | API → serviço → **MongoDB real**: filtros executados no banco, dados legados, índices | `@SpringBootTest`, Flapdoodle (MongoDB 7.0 embarcado) | real (embarcado) |
| Manual / sistema | Aplicação em execução: API via `curl` e tela no navegador | curl, Chrome headless | MongoDB local |

O relógio é injetável (`Clock`), por isso testes que dependem de "hoje" (período padrão de 30 dias) são determinísticos.

### Como executar

```bash
mvnw.cmd test          # Windows
./mvnw test            # Linux/macOS
```

Não precisa de banco configurado: o teste de integração baixa e sobe um MongoDB temporário na primeira execução.

## 2. Testes automatizados do dashboard (48 casos)

Os outros 46 testes da suíte são da atividade M1 (pedidos, clientes, usuários, segurança e telas) e continuam passando, o que mostra que o dashboard não quebrou nada que já existia.

### 2.1 `CalculadoraIndicadoresTest` — regras de cálculo (15)

| Caso de teste | Requisito | Verifica |
|---|---|---|
| `faturamentoETicketMedioIgnoramPedidosCancelados` | RF01 · RN01 | 3 pedidos (100 + 60 + 50 cancelado) → faturamento 160, ticket 80, taxa de cancelamento 33,3%, 4 itens, 1 em andamento |
| `descontoReduzFaturamentoEEhSomadoNosDescontos` | RF01 | Pedido de 100 com 10% → faturamento 90, descontos 10 |
| `comparativoCalculaVariacaoEmRelacaoAoPeriodoAnterior` | RF01 · RN03/RN06 | Período anterior de mesma duração; variações de +200%, +100% e +50% |
| `semVendasNoPeriodoAnteriorAVariacaoFicaNula` | RF01 · RN06 | Sem base → `null` (sem divisão por zero) |
| `periodoSemPedidosRetornaZerosSemErro` | RF07 | Período vazio → zeros |
| `tempoMedioAteSaidaUsaEntradaESaida` | RF01 | Saídas em 30 e 50 min → média de 40 min; pedido sem saída ignorado |
| `pedidosAntigosComCamposNulosNaoQuebramOsCalculos` | RF07 | Documentos sem status/itens/valores e itens `null` não geram exceção e são contados em `qualidadeDados` |
| `seriePorDiaIncluiDiasSemVendaComZero` | RF02 | 7 dias com dias vazios em zero; cancelado fora da série |
| `seriePorSemanaComecaNaSegundaEPorMesUsaRotuloAbreviado` | RF02 · RN07 | Semana inicia na segunda; rótulos `Sem. 26/10`, `out/26`, `nov/26` |
| `distribuicaoPorHoraEDiaDaSemanaTemTodosOsPontos` | RF02 | 24 horas e 7 dias, de SEG a DOM |
| `vendasPorCategoriaETipoDeEntrega` | RF02 · RN05 | Participação por categoria, "Sem categoria" para produto excluído, ticket por tipo |
| `situacaoListaTodosOsStatusInclusiveSemPedidos` | RF03 | 7 status sempre presentes; em andamento, finalizados e cancelados |
| `pedidosRecentesInformamMinutosDesdeAEntrada` | RF03 | Código curto, descrição e minutos desde a entrada |
| `rankingOrdenaPorQuantidadeOuFaturamentoESemCancelados` | RF04 | Ordem por quantidade x faturamento, participação, limite, cancelados fora |
| `filtroDeCategoriaConsideraSoOsItensDaCategoriaComDescontoRateado` | RF05 · RN04 | Pedido de R$ 60 com 10% de desconto, filtro "bebida" → R$ 9,00 e desconto de R$ 1,00 |

### 2.2 `DashboardServiceTest` — filtros e orquestração (13)

| Caso de teste | Requisito | Verifica |
|---|---|---|
| `semParametrosUsaOsUltimos30DiasAteHoje` | RF05 | Período padrão |
| `converteStatusRepetidosOuSeparadosPorVirgulaSemDiferenciarMaiusculas` | RF05 | `finalizado,CANCELADO` + ` pronto ` → 3 status; texto aparado |
| `dataInicialPosteriorAFinalEhRejeitada` | RF05 | Validação do período |
| `dataEmFormatoInvalidoEhRejeitadaComOrientacao` | RF05 | Mensagem cita o parâmetro e o formato AAAA-MM-DD |
| `periodoAcimaDoLimiteEhRejeitado` | RNF06 | Limite de 366 dias |
| `statusOuTipoDeEntregaInexistentesListamOsValoresAceitos` | RF05 | Mensagem lista os valores válidos |
| `limiteEAgrupamentoInvalidosSaoRejeitados` | RNF06 | Limite 0 e 51; agrupamento `ANO` |
| `filtroDeCategoriaViraListaDeProdutosParaOBanco` | RF05 | Categoria → ids dos produtos (sem diferenciar maiúsculas e espaços) |
| `categoriaSemProdutosResultaEmListaVazia` | RF05 | Nenhum produto → nenhum pedido |
| `semFiltroDeProdutoNaoRestringeItens` | RF05 | Sem filtro → sem restrição de itens |
| `resumoConsultaTambemOPeriodoAnterior` | RF01 | Duas consultas: período atual e anterior |
| `recentesUsaLimitePadrao` | RF03 | Limite 10 |
| `opcoesDeFiltroUnificamCategoriasEInformamPeriodoPadrao` | RF05 | "Pizza" e " pizza " viram uma categoria |

### 2.3 `DashboardApiTest` — camada HTTP com o serviço real (10)

| Caso de teste | Requisito | Verifica |
|---|---|---|
| `anonimoRecebe401` | RNF01 | Sem sessão → 401 |
| `atendenteNaoAcessaODashboard` | RNF01 | ATENDENTE → 403 na API e na tela; o banco não é consultado |
| `apiEhSomenteLeitura` | RNF02 | POST → 403 |
| `adminAcessaATelaDoDashboard` | RF06 | Tela renderiza e carrega `dashboard.js` |
| `resumoRetornaIndicadores` | RF01 | Campos e valores do JSON |
| `vendasPorHoraRetorna24Pontos` | RF02 | 24 pontos; pedido das 19h no ponto 19 |
| `vendasPorDiaEhOPadrao` | RF02 | Agrupamento padrão DIA com 30 pontos |
| `demaisEndpointsRespondem` | RF03/RF04/RF05 | status, recentes, ranking, categorias, tipos de entrega e filtros |
| `filtrosInvalidosRetornam400ComMensagem` | RF05 · RF07 | 6 cenários de 400 com mensagem e caminho |
| `bancoIndisponivelRetorna503` | RF07 · RNF07 | Falha do banco → 503 com mensagem |

### 2.4 `DashboardIntegracaoTest` — integração com MongoDB (8)

Grava pedidos no MongoDB (inclusive dois documentos no formato da versão antiga) e consulta pela API.

| Caso de teste | Verifica |
|---|---|
| `resumoCalculaOsIndicadoresAPartirDoBanco` | Indicadores e comparativo com dados reais; o legado `ENTREGUE` com valor em texto entra no faturamento; o pedido sem data é contado à parte |
| `filtroDeStatusEhAplicadoNoBancoInclusiveComValoresLegados` | `status=FINALIZADO` encontra também documentos gravados como `ENTREGUE` |
| `filtrosDeTipoDeEntregaEPeriodo` | Filtro por tipo e por um único dia, executados no banco |
| `filtroDeCategoriaRestringePedidosEItens` | Categoria e produto: pedidos que contêm o item, valores só dos itens |
| `rankingCategoriasERecentes` | Ranking, categorias e ordenação/limite dos recentes feitos pelo banco |
| `vendasPorDiaPreencheOPeriodoPadrao` | 30 pontos; ontem e hoje nas posições certas |
| `filtrosDisponiveisVemDoCadastroEDosPedidos` | Categorias, produtos e primeiro pedido vindos do banco |
| `indicesDoDashboardSaoCriadosNaInicializacao` | Os 3 índices existem na coleção `pedidos` |

### 2.5 Configuração (2)

| Caso de teste | Verifica |
|---|---|
| `MongoTimeoutConfigTest.aplicaLimitesDeTempoAoDriver` | Limites de tempo aplicados ao driver do MongoDB (RNF07) |
| `DadosDemonstracaoConfigTest.pedidosGeradosRespeitamPeriodoFluxoEValores` | Dados de demonstração respeitam o fluxo de status, o desconto máximo de 20% e o cálculo de valores |

## 3. Testes manuais da API (aplicação em execução)

Executados com `curl` contra a aplicação rodando com MongoDB local e os dados de demonstração (1.290 pedidos em 90 dias). Cada requisição e sua resposta estão em [`evidencias/api/execucao-manual-api.md`](evidencias/api/execucao-manual-api.md).

| # | Cenário | Usuário | Esperado | Obtido |
|---|---|---|:-:|:-:|
| 1 | Resumo dos últimos 30 dias | gerente01 | 200 | ✅ 200 |
| 2 | Vendas por dia (7 dias) | gerente01 | 200 | ✅ 200 |
| 3 | Horários de pico | gerente01 | 200 | ✅ 200 |
| 4 | Vendas por mês (90 dias) | admin | 200 | ✅ 200 |
| 5 | Vendas por categoria | gerente01 | 200 | ✅ 200 |
| 6 | Entrega x retirada | gerente01 | 200 | ✅ 200 |
| 7 | Situação dos pedidos de hoje | gerente01 | 200 | ✅ 200 |
| 8 | Pedidos recentes (limite 3) | gerente01 | 200 | ✅ 200 |
| 9 | Ranking por quantidade | gerente01 | 200 | ✅ 200 |
| 10 | Ranking por faturamento | gerente01 | 200 | ✅ 200 |
| 11 | Filtros combinados (finalizados + entrega + Pizza) | gerente01 | 200 | ✅ 200 |
| 12 | Opções de filtro | gerente01 | 200 | ✅ 200 |
| 13 | Data inicial depois da final | gerente01 | 400 | ✅ 400 |
| 14 | Data em formato inválido | gerente01 | 400 | ✅ 400 |
| 15 | Status inexistente | gerente01 | 400 | ✅ 400 |
| 16 | Período maior que 366 dias | gerente01 | 400 | ✅ 400 |
| 17 | Limite fora da faixa | gerente01 | 400 | ✅ 400 |
| 18 | Perfil ATENDENTE | atendente01 | 403 | ✅ 403 |
| 19 | Sem autenticação | — | 401 | ✅ 401 |

**Conferência de consistência dos números (cenário 1):** faturamento R$ 38.279,23 ÷ 450 pedidos válidos = R$ 85,06 de ticket médio ✔; 32 cancelados ÷ 482 pedidos = 6,6% ✔; soma das categorias (28.795,63 + 4.335,92 + 3.007,63 + 2.140,05) = 38.279,23 ✔; entrega + retirada (270 + 180) = 450 pedidos válidos ✔.

## 4. Testes da interface

Capturas do navegador (Chrome) com a aplicação em execução, em [`evidencias/telas/`](evidencias/telas/).

| Tela | O que comprova |
|---|---|
| [01-dashboard-completo.png](evidencias/telas/01-dashboard-completo.png) | Painel completo: indicadores com comparativo (RF01), gráficos (RF02), situação e pedidos recentes (RF03), ranking (RF04), filtros (RF05) |
| [02-filtro-90-dias-semana-faturamento.png](evidencias/telas/02-filtro-90-dias-semana-faturamento.png) | Período de 90 dias, agrupamento semanal e ranking por faturamento; "Sem base de comparação" quando não há dados anteriores |
| [03-filtro-categoria-status.png](evidencias/telas/03-filtro-categoria-status.png) | Filtro combinado de categoria "Bebida" e status "Finalizado" aplicado a todo o painel |
| [04-validacao-filtro.png](evidencias/telas/04-validacao-filtro.png) | Validação: data inicial posterior à final |
| [05-ranking-em-tabela.png](evidencias/telas/05-ranking-em-tabela.png) | Visão em tabela do gráfico (acessibilidade) |
| [06-dashboard-celular.png](evidencias/telas/06-dashboard-celular.png) | Layout em celular (390 px) sem rolagem horizontal |
| [07-atendente-acesso-negado.png](evidencias/telas/07-atendente-acesso-negado.png) | ATENDENTE não vê o menu "Dashboard" e recebe 403 ao acessar `/dashboard` direto |
| [08-banco-indisponivel.png](evidencias/telas/08-banco-indisponivel.png) | MongoDB derrubado com o painel aberto: aviso em ~11 s, últimos dados mantidos, "Dados de 19:10:37" e botão "Tentar novamente" (RF07) |

Durante a navegação automatizada não houve erros no console do navegador.

## 5. Defeitos encontrados pelos testes e corrigidos

| # | Defeito | Como foi encontrado | Correção |
|---|---|---|---|
| 1 | Com o banco fora do ar, cada seção levava **mais de 90 s** para falhar (o driver do MongoDB espera 30 s por consulta, e cada endpoint faz várias). | Teste manual derrubando o MongoDB com o painel aberto. | `MongoTimeoutConfig` (desiste em 5 s → 503) e limite de 20 s por requisição no navegador. Depois da correção: aviso em ~11 s. |
| 2 | Valor na ponta da maior barra ("R$ 78 mil · 76,2%") cortado no gráfico de categorias. | Inspeção das capturas de tela. | A folga à direita passou a ser calculada pela largura real do maior rótulo. |
| 3 | No celular, o botão ☰ do menu cobria o título do dashboard. | Captura a 390 px. | Recuo do cabeçalho abaixo de 768 px. |
| 4 | A suavização da curva de vendas podia desenhar valores acima ou abaixo dos reais. | Inspeção das capturas. | Interpolação monotônica. |
| 5 | Requisição de `favicon.ico` com 404 no console em todas as páginas. | Log de console na navegação automatizada. | Ícone definido no `layout.html`. |

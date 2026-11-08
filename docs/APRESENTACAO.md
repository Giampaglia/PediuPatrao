# Roteiro da apresentação (vídeo de até 3 minutos)

Objetivo: mostrar o dashboard funcionando e justificar as principais decisões de análise e projeto.

## Preparação (antes de gravar)

1. Inicie a aplicação **com os dados de demonstração** (ver [README](../README.md#dashboard-administrativo-atividade-m2)):
   `mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--pediupatrao.demo.gerar-pedidos=true"`
2. Deixe abertas três abas: o dashboard logado como `gerente01`, este roteiro e o [`docs/API.md`](API.md) no GitHub.
3. Teste o microfone e use tela cheia com zoom de 100% no navegador.

## Roteiro

| Tempo | Tela | O que falar |
|---|---|---|
| **0:00–0:20** | Dashboard (topo) | "Este é o dashboard administrativo do Pediu Patrão. Ele foi feito para o gerente acompanhar vendas, pedidos e tendências e decidir com base em dados. Só ADMIN e GERENTE acessam." |
| **0:20–0:50** | Indicadores (RF01) | "No topo ficam os indicadores do período: faturamento, pedidos, ticket médio, itens, cancelamentos, descontos e tempo médio até a saída. Cada um é comparado com o período anterior de mesma duração: aqui o faturamento subiu 16,5%. Pedido cancelado não conta como venda; essa regra fica numa classe de cálculo testada isoladamente." |
| **0:50–1:25** | Gráficos (RF02, RF03) | Troque Dia → Semana e Faturamento → Pedidos. "A evolução pode ser vista por dia, semana ou mês. O horário de pico é entre 20h e 21h e o sábado é o dia mais forte, informação útil para escalar funcionários. Ao lado, a situação dos pedidos por status e, embaixo, os pedidos recentes com o tempo em andamento." |
| **1:25–1:45** | Ranking (RF04) | Troque Quantidade → Faturamento. "Em quantidade, as esfihas lideram; em faturamento, as pizzas. Por isso o ranking tem os dois critérios." Clique em "Ver tabela": "Todo gráfico tem uma visão em tabela, por acessibilidade." |
| **1:45–2:10** | Filtros (RF05) | Selecione categoria "Bebida" e clique em Aplicar. "Os filtros valem para o painel inteiro e ficam na URL. Com filtro de categoria, o faturamento soma só os itens daquela categoria, com o desconto rateado." Coloque a data inicial depois da final e clique em Aplicar para mostrar a validação. |
| **2:10–2:35** | Aba da API / terminal (RF06) | Mostre o `API.md`. "O painel não acessa o banco: consome uma API REST com 8 endpoints de leitura. Separei por recurso para que cada gráfico recarregue sozinho e uma falha não derrube o resto. Os filtros rodam no MongoDB com índices, e os erros seguem um formato único: 400 para filtro inválido, 401, 403 e 503 para banco fora do ar." |
| **2:35–2:55** | Topo do dashboard (RF07) | "O painel se atualiza a cada 60 segundos e pausa quando a aba não está visível. Nos testes descobri que, com o banco fora do ar, a tela ficava minutos esperando; limitei o tempo do driver e agora o aviso aparece em segundos, mantendo os últimos dados." Mostre a captura `08-banco-indisponivel.png`. |
| **2:55–3:00** | GitHub (branches) | "O código está no GitHub no fluxo gitflow, com a branch `feature/dashboard-api`, e 94 testes automatizados passando." |

## Decisões para justificar (se houver perguntas)

- **Endpoints por recurso, e não um único endpoint**: carga independente por gráfico, falha isolada e reuso por outros clientes.
- **Filtrar no banco e calcular em Java**: o índice reduz o volume; o `BigDecimal` é gravado como texto pelo Spring Data; os documentos antigos têm campos nulos; as regras ficam testáveis numa classe pura.
- **Comparativo automático**: um número isolado não mostra tendência.
- **Segurança em duas camadas** (URL + `@PreAuthorize`) e API somente leitura.
- **Gráficos sem eixo duplo** e com paleta validada para daltonismo.
- **MongoDB embarcado nos testes**: os testes de integração rodam em qualquer máquina, sem depender do Atlas.

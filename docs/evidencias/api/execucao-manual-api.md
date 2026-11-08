# Execução manual da API do dashboard

Requisições reais feitas com `curl` contra a aplicação em execução (MongoDB local com os dados de demonstração).
Data da execução: 08/11/2026 19:00.

## RF01 — Indicadores gerais (últimos 30 dias)

`GET /api/dashboard/resumo` — usuário: `gerente01` — **HTTP 200**

```json
{
  "periodo": {
    "dataInicio": "2026-10-10",
    "dataFim": "2026-11-08",
    "dias": 30
  },
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
    "periodoAnterior": {
      "dataInicio": "2026-09-10",
      "dataFim": "2026-10-09",
      "dias": 30
    },
    "faturamento": 32864.25,
    "pedidosValidos": 399,
    "ticketMedio": 82.37,
    "variacaoFaturamento": 16.5,
    "variacaoPedidos": 12.8,
    "variacaoTicketMedio": 3.3
  },
  "qualidadeDados": {
    "pedidosSemStatus": 0,
    "pedidosSemValor": 0,
    "pedidosSemDataEntrada": 0
  },
  "geradoEm": "2026-11-08T19:00:57.9008307"
}
```

## RF02 — Vendas por dia

`GET /api/dashboard/vendas?agrupamento=DIA&dataInicio=2026-11-02&dataFim=2026-11-08` — usuário: `gerente01` — **HTTP 200**

```json
{
  "agrupamento": "DIA",
  "periodo": {
    "dataInicio": "2026-11-02",
    "dataFim": "2026-11-08",
    "dias": 7
  },
  "totalPedidos": 91,
  "faturamentoTotal": 7707.86,
  "pontos": [
    {
      "chave": "2026-11-02",
      "rotulo": "02/11",
      "pedidos": 7,
      "faturamento": 561,
      "ticketMedio": 80.14
    },
    {
      "chave": "2026-11-03",
      "rotulo": "03/11",
      "pedidos": 13,
      "faturamento": 1168.1,
      "ticketMedio": 89.85
    },
    {
      "chave": "2026-11-04",
      "rotulo": "04/11",
      "pedidos": 8,
      "faturamento": 503,
      "ticketMedio": 62.88
    },
    {
      "chave": "2026-11-05",
      "rotulo": "05/11",
      "pedidos": 13,
      "faturamento": 935.15,
      "ticketMedio": 71.93
    },
    {
      "chave": "2026-11-06",
      "rotulo": "06/11",
      "pedidos": 19,
      "faturamento": 1824.41,
      "ticketMedio": 96.02
    },
... (resposta completa em vendas-dia.json)
```

## RF02 — Horários de pico

`GET /api/dashboard/vendas?agrupamento=HORA_DO_DIA` — usuário: `gerente01` — **HTTP 200**

```json
{
  "agrupamento": "HORA_DO_DIA",
  "periodo": {
    "dataInicio": "2026-10-10",
    "dataFim": "2026-11-08",
    "dias": 30
  },
  "totalPedidos": 450,
  "faturamentoTotal": 38279.23,
  "pontos": [
    {
      "chave": "0",
      "rotulo": "00h",
      "pedidos": 0,
      "faturamento": 0,
      "ticketMedio": 0
    },
    {
      "chave": "1",
      "rotulo": "01h",
      "pedidos": 0,
      "faturamento": 0,
      "ticketMedio": 0
    },
    {
      "chave": "2",
      "rotulo": "02h",
      "pedidos": 0,
      "faturamento": 0,
      "ticketMedio": 0
... (resposta completa em vendas-hora.json)
```

## RF02 — Vendas por mês (ADMIN)

`GET /api/dashboard/vendas?agrupamento=MES&dataInicio=2026-08-11&dataFim=2026-11-08` — usuário: `admin` — **HTTP 200**

```json
{
  "agrupamento": "MES",
  "periodo": {
    "dataInicio": "2026-08-11",
    "dataFim": "2026-11-08",
    "dias": 90
  },
  "totalPedidos": 1218,
  "faturamentoTotal": 102533.24,
  "pontos": [
    {
      "chave": "2026-08",
      "rotulo": "ago/26",
      "pedidos": 256,
      "faturamento": 21876.84,
      "ticketMedio": 85.46
    },
    {
      "chave": "2026-09",
      "rotulo": "set/26",
      "pedidos": 385,
      "faturamento": 32070.77,
      "ticketMedio": 83.3
    },
    {
      "chave": "2026-10",
      "rotulo": "out/26",
      "pedidos": 468,
      "faturamento": 39216.55,
      "ticketMedio": 83.8
    },
    {
      "chave": "2026-11",
      "rotulo": "nov/26",
      "pedidos": 109,
      "faturamento": 9369.08,
      "ticketMedio": 85.95
    }
  ]
}
```

## RF02 — Vendas por categoria

`GET /api/dashboard/vendas/categorias` — usuário: `gerente01` — **HTTP 200**

```json
{
  "periodo": {
    "dataInicio": "2026-10-10",
    "dataFim": "2026-11-08",
    "dias": 30
  },
  "faturamentoTotal": 38279.23,
  "categorias": [
    {
      "categoria": "Pizza",
      "quantidade": 557,
      "faturamento": 28795.63,
      "participacao": 75.2
    },
    {
      "categoria": "Esfiha",
      "quantidade": 601,
      "faturamento": 4335.92,
      "participacao": 11.3
    },
    {
      "categoria": "Lanche",
      "quantidade": 116,
      "faturamento": 3007.63,
      "participacao": 7.9
    },
    {
      "categoria": "Bebida",
      "quantidade": 192,
      "faturamento": 2140.05,
      "participacao": 5.6
    }
  ]
}
```

## RF02 — Entrega x retirada

`GET /api/dashboard/vendas/tipos-entrega` — usuário: `gerente01` — **HTTP 200**

```json
{
  "periodo": {
    "dataInicio": "2026-10-10",
    "dataFim": "2026-11-08",
    "dias": 30
  },
  "totalPedidos": 450,
  "tipos": [
    {
      "tipo": "ENTREGA",
      "descricao": "Entrega",
      "pedidos": 270,
      "faturamento": 22958.04,
      "ticketMedio": 85.03,
      "participacao": 60
    },
    {
      "tipo": "RETIRADA",
      "descricao": "Retirada no balcão",
      "pedidos": 180,
      "faturamento": 15321.19,
      "ticketMedio": 85.12,
      "participacao": 40
    }
  ]
}
```

## RF03 — Situação dos pedidos (hoje)

`GET /api/dashboard/pedidos/status?dataInicio=2026-11-08&dataFim=2026-11-08` — usuário: `gerente01` — **HTTP 200**

```json
{
  "periodo": {
    "dataInicio": "2026-11-08",
    "dataFim": "2026-11-08",
    "dias": 1
  },
  "total": 8,
  "emAndamento": 4,
  "finalizados": 3,
  "cancelados": 1,
  "porStatus": [
    {
      "status": "RECEBIDO",
      "descricao": "Recebido",
      "quantidade": 0,
      "percentual": 0,
      "valorTotal": 0
    },
    {
      "status": "EM_PREPARACAO",
      "descricao": "Em preparação",
      "quantidade": 4,
      "percentual": 50,
      "valorTotal": 181.5
    },
    {
      "status": "PRONTO",
      "descricao": "Pronto",
      "quantidade": 0,
      "percentual": 0,
      "valorTotal": 0
    },
    {
      "status": "SAIU_PARA_ENTREGA",
      "descricao": "Saiu para entrega",
      "quantidade": 0,
      "percentual": 0,
      "valorTotal": 0
    },
    {
      "status": "RETIRADO",
      "descricao": "Retirado",
      "quantidade": 0,
      "percentual": 0,
      "valorTotal": 0
    },
    {
      "status": "FINALIZADO",
      "descricao": "Finalizado",
      "quantidade": 3,
      "percentual": 37.5,
      "valorTotal": 320
    },
    {
      "status": "CANCELADO",
      "descricao": "Cancelado",
      "quantidade": 1,
      "percentual": 12.5,
      "valorTotal": 58.9
    }
  ]
}
```

## RF03 — Pedidos recentes

`GET /api/dashboard/pedidos/recentes?limite=3` — usuário: `gerente01` — **HTTP 200**

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
    "valorTotal": 12,
    "dataHoraEntrada": "2026-11-08T18:55:09",
    "minutosDesdeEntrada": 5
  },
  {
    "id": "6af0f0f41679014347f7c766",
    "codigo": "F7C766",
    "clienteNome": "Olívia Cardoso",
    "status": "EM_PREPARACAO",
    "statusDescricao": "Em preparação",
    "tipoEntrega": "ENTREGA",
    "quantidadeItens": 1,
    "valorTotal": 4,
    "dataHoraEntrada": "2026-11-08T18:47:08",
    "minutosDesdeEntrada": 13
  },
  {
    "id": "6af0f0f41679014347f7c768",
    "codigo": "F7C768",
    "clienteNome": "Karina Teixeira",
    "status": "EM_PREPARACAO",
    "statusDescricao": "Em preparação",
    "tipoEntrega": "ENTREGA",
    "quantidadeItens": 3,
    "valorTotal": 71.9,
    "dataHoraEntrada": "2026-11-08T18:45:33",
    "minutosDesdeEntrada": 15
  }
]
```

## RF04 — Produtos mais vendidos por quantidade

`GET /api/dashboard/produtos/mais-vendidos?limite=5` — usuário: `gerente01` — **HTTP 200**

```json
{
  "periodo": {
    "dataInicio": "2026-10-10",
    "dataFim": "2026-11-08",
    "dias": 30
  },
  "criterio": "QUANTIDADE",
  "limite": 5,
  "totalItensVendidos": 1466,
  "faturamentoTotal": 38279.23,
  "produtos": [
    {
      "posicao": 1,
      "produtoId": "6af0f0f41679014347f7c24d",
      "nome": "Esfiha de Carne",
      "categoria": "Esfiha",
      "quantidade": 326,
      "faturamento": 2422.12,
      "pedidos": 76,
      "participacao": 22.2
    },
    {
      "posicao": 2,
      "produtoId": "6af0f0f41679014347f7c24e",
      "nome": "Esfiha de Queijo",
      "categoria": "Esfiha",
      "quantidade": 275,
      "faturamento": 1913.8,
      "pedidos": 63,
      "participacao": 18.8
    },
    {
      "posicao": 3,
      "produtoId": "6af0f0f31679014347f7c247",
      "nome": "Pizza Calabresa",
      "categoria": "Pizza",
      "quantidade": 127,
      "faturamento": 6287.4,
      "pedidos": 107,
      "participacao": 8.7
    },
    {
      "posicao": 4,
      "produtoId": "6af0f0f41679014347f7c251",
      "nome": "Refrigerante 2L",
      "categoria": "Bebida",
      "quantidade": 120,
      "faturamento": 1431.6,
      "pedidos": 103,
      "participacao": 8.2
... (resposta completa em ranking-quantidade.json)
```

## RF04 — Produtos mais vendidos por faturamento

`GET /api/dashboard/produtos/mais-vendidos?ordenarPor=FATURAMENTO&limite=3` — usuário: `gerente01` — **HTTP 200**

```json
{
  "periodo": {
    "dataInicio": "2026-10-10",
    "dataFim": "2026-11-08",
    "dias": 30
  },
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
      "faturamento": 6287.4,
      "pedidos": 107,
      "participacao": 16.4
    },
    {
      "posicao": 2,
      "produtoId": "6af0f0f41679014347f7c24a",
      "nome": "Pizza Frango com Catupiry",
      "categoria": "Pizza",
      "quantidade": 105,
      "faturamento": 5928.97,
      "pedidos": 89,
      "participacao": 15.5
    },
    {
      "posicao": 3,
      "produtoId": "6af0f0f41679014347f7c248",
      "nome": "Pizza Mussarela",
      "categoria": "Pizza",
      "quantidade": 119,
      "faturamento": 5314.49,
      "pedidos": 99,
      "participacao": 13.9
... (resposta completa em ranking-faturamento.json)
```

## RF05 — Filtros combinados (finalizados, entrega, categoria Pizza)

`GET /api/dashboard/resumo?dataInicio=2026-10-10&dataFim=2026-11-08&status=FINALIZADO&tipoEntrega=ENTREGA&categoria=Pizza` — usuário: `gerente01` — **HTTP 200**

```json
{
  "periodo": {
    "dataInicio": "2026-10-10",
    "dataFim": "2026-11-08",
    "dias": 30
  },
  "faturamento": 16879.47,
  "totalPedidos": 204,
  "pedidosValidos": 204,
  "pedidosCancelados": 0,
  "taxaCancelamento": 0,
  "ticketMedio": 82.74,
  "itensVendidos": 326,
  "totalDescontos": 168.43,
  "pedidosComDesconto": 16,
  "pedidosEmAndamento": 0,
  "tempoMedioAteSaidaMinutos": 35.1,
  "comparativo": {
    "periodoAnterior": {
      "dataInicio": "2026-09-10",
      "dataFim": "2026-10-09",
      "dias": 30
    },
    "faturamento": 14538.35,
    "pedidosValidos": 169,
... (resposta completa em resumo-filtrado.json)
```

## RF05 — Opções de filtro

`GET /api/dashboard/filtros` — usuário: `gerente01` — **HTTP 200**

```json
{
  "periodoPadrao": {
    "dataInicio": "2026-10-10",
    "dataFim": "2026-11-08",
    "dias": 30
  },
  "limitePeriodoDias": 366,
  "primeiroPedido": "2026-08-11T11:07:36",
  "ultimoPedido": "2026-11-08T18:55:09",
  "status": [
    {
      "valor": "RECEBIDO",
      "descricao": "Recebido"
    },
    {
      "valor": "EM_PREPARACAO",
      "descricao": "Em preparação"
    },
    {
      "valor": "PRONTO",
      "descricao": "Pronto"
    },
    {
      "valor": "SAIU_PARA_ENTREGA",
      "descricao": "Saiu para entrega"
    },
    {
      "valor": "RETIRADO",
      "descricao": "Retirado"
    },
    {
      "valor": "FINALIZADO",
      "descricao": "Finalizado"
    },
    {
      "valor": "CANCELADO",
      "descricao": "Cancelado"
    }
  ],
  "tiposEntrega": [
... (resposta completa em filtros.json)
```

## Erro 400 — data inicial depois da final

`GET /api/dashboard/resumo?dataInicio=2026-11-10&dataFim=2026-11-01` — usuário: `gerente01` — **HTTP 400**

```json
{
  "dataHora": "2026-11-08T19:01:05.501076900",
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "A data inicial (dataInicio) não pode ser posterior à data final (dataFim).",
  "caminho": "/api/dashboard/resumo"
}
```

## Erro 400 — data em formato inválido

`GET /api/dashboard/resumo?dataInicio=08/11/2026` — usuário: `gerente01` — **HTTP 400**

```json
{
  "dataHora": "2026-11-08T19:01:06.077590600",
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "Data inválida no parâmetro 'dataInicio': '08/11/2026'. Use o formato AAAA-MM-DD, ex.: 2026-11-08.",
  "caminho": "/api/dashboard/resumo"
}
```

## Erro 400 — status inexistente

`GET /api/dashboard/pedidos/status?status=VOANDO` — usuário: `gerente01` — **HTTP 400**

```json
{
  "dataHora": "2026-11-08T19:01:06.729594200",
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "Valor inválido no parâmetro 'status': 'VOANDO'. Valores aceitos: RECEBIDO, EM_PREPARACAO, PRONTO, SAIU_PARA_ENTREGA, RETIRADO, FINALIZADO, CANCELADO.",
  "caminho": "/api/dashboard/pedidos/status"
}
```

## Erro 400 — período maior que 366 dias

`GET /api/dashboard/resumo?dataInicio=2024-01-01&dataFim=2026-11-08` — usuário: `gerente01` — **HTTP 400**

```json
{
  "dataHora": "2026-11-08T19:01:07.341993400",
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "O período consultado não pode ultrapassar 366 dias (informado: 1043 dias).",
  "caminho": "/api/dashboard/resumo"
}
```

## Erro 400 — limite fora da faixa

`GET /api/dashboard/produtos/mais-vendidos?limite=500` — usuário: `gerente01` — **HTTP 400**

```json
{
  "dataHora": "2026-11-08T19:01:07.877589",
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "O parâmetro 'limite' deve estar entre 1 e 50 (informado: 500).",
  "caminho": "/api/dashboard/produtos/mais-vendidos"
}
```

## Erro 403 — perfil ATENDENTE sem acesso

`GET /api/dashboard/resumo` — usuário: `atendente01` — **HTTP 403**

```json
{
  "status": 403,
  "erro": "Forbidden",
  "mensagem": "Acesso negado: Perfil sem permissão para a operação.",
  "caminho": "/api/dashboard/resumo"
}
```

## Erro 401 — sem autenticação

`GET /api/dashboard/resumo` — usuário: `anonimo` — **HTTP 401**

```json

```


package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

/** RF02 — série de vendas agrupada (pontos sem venda aparecem com zero). */
public record SerieVendas(
        Agrupamento agrupamento,
        Periodo periodo,
        long totalPedidos,
        BigDecimal faturamentoTotal,
        List<Ponto> pontos) {

    public record Ponto(
            String chave,
            String rotulo,
            long pedidos,
            BigDecimal faturamento,
            BigDecimal ticketMedio) {
    }
}

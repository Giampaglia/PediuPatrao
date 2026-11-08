package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

/** RF04 — produtos mais vendidos (pedidos cancelados não entram). */
public record RankingProdutos(
        Periodo periodo,
        CriterioRanking criterio,
        int limite,
        long totalItensVendidos,
        BigDecimal faturamentoTotal,
        List<ProdutoVendido> produtos) {

    /** @param participacao percentual do produto no total, segundo o critério do ranking */
    public record ProdutoVendido(
            int posicao,
            String produtoId,
            String nome,
            String categoria,
            long quantidade,
            BigDecimal faturamento,
            long pedidos,
            BigDecimal participacao) {
    }
}

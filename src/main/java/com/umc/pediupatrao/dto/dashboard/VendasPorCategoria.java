package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

/** RF02 — vendas agrupadas pela categoria (tipo) do produto. */
public record VendasPorCategoria(
        Periodo periodo,
        BigDecimal faturamentoTotal,
        List<Categoria> categorias) {

    /** @param participacao percentual do faturamento total */
    public record Categoria(
            String categoria,
            long quantidade,
            BigDecimal faturamento,
            BigDecimal participacao) {
    }
}

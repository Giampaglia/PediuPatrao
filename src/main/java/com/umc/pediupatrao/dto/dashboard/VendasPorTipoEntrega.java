package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.TipoEntrega;
import java.math.BigDecimal;
import java.util.List;

/** RF02 — comparação entre pedidos para entrega e para retirada. */
public record VendasPorTipoEntrega(
        Periodo periodo,
        long totalPedidos,
        List<Tipo> tipos) {

    /** @param participacao percentual dos pedidos válidos */
    public record Tipo(
            TipoEntrega tipo,
            String descricao,
            long pedidos,
            BigDecimal faturamento,
            BigDecimal ticketMedio,
            BigDecimal participacao) {
    }
}

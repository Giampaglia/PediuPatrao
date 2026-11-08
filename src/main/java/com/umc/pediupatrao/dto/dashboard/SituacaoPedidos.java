package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;
import java.util.List;

/** RF03 — quantidade de pedidos por status (todos os status aparecem, mesmo com zero). */
public record SituacaoPedidos(
        Periodo periodo,
        long total,
        long emAndamento,
        long finalizados,
        long cancelados,
        List<QuantidadePorStatus> porStatus) {

    public record QuantidadePorStatus(
            StatusPedido status,
            String descricao,
            long quantidade,
            BigDecimal percentual,
            BigDecimal valorTotal) {
    }
}

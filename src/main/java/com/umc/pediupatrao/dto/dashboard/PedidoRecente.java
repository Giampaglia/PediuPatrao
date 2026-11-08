package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** RF03 — pedido resumido para a lista de pedidos recentes do dashboard. */
public record PedidoRecente(
        String id,
        String codigo,
        String clienteNome,
        StatusPedido status,
        String statusDescricao,
        TipoEntrega tipoEntrega,
        int quantidadeItens,
        BigDecimal valorTotal,
        LocalDateTime dataHoraEntrada,
        Long minutosDesdeEntrada) {
}

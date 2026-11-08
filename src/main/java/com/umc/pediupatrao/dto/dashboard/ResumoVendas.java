package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RF01 — indicadores gerais de vendas do período filtrado.
 *
 * Regras: faturamento, ticket médio e itens vendidos consideram somente
 * pedidos válidos (com status reconhecido e diferente de CANCELADO).
 */
public record ResumoVendas(
        Periodo periodo,
        BigDecimal faturamento,
        long totalPedidos,
        long pedidosValidos,
        long pedidosCancelados,
        BigDecimal taxaCancelamento,
        BigDecimal ticketMedio,
        long itensVendidos,
        BigDecimal totalDescontos,
        long pedidosComDesconto,
        long pedidosEmAndamento,
        BigDecimal tempoMedioAteSaidaMinutos,
        Comparativo comparativo,
        QualidadeDados qualidadeDados,
        LocalDateTime geradoEm) {

    /** Mesmos indicadores no período anterior de mesma duração e a variação percentual. */
    public record Comparativo(
            Periodo periodoAnterior,
            BigDecimal faturamento,
            long pedidosValidos,
            BigDecimal ticketMedio,
            BigDecimal variacaoFaturamento,
            BigDecimal variacaoPedidos,
            BigDecimal variacaoTicketMedio) {
    }

    /** RF07 — registros com dados incompletos, tratados em vez de quebrar os cálculos. */
    public record QualidadeDados(
            long pedidosSemStatus,
            long pedidosSemValor,
            long pedidosSemDataEntrada) {
    }
}

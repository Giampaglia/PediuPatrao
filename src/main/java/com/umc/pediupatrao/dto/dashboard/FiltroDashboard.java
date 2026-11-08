package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Filtro já validado. O período considera a data/hora de ENTRADA do pedido:
 * de dataInicio 00:00 (inclusive) até dataFim + 1 dia 00:00 (exclusive).
 *
 * @param status conjunto vazio significa "todos os status"
 */
public record FiltroDashboard(
        LocalDate dataInicio,
        LocalDate dataFim,
        Set<StatusPedido> status,
        TipoEntrega tipoEntrega,
        String categoria,
        String produtoId) {

    public FiltroDashboard {
        status = status == null ? Set.of() : Set.copyOf(status);
    }

    public LocalDateTime inicio() {
        return dataInicio.atStartOfDay();
    }

    public LocalDateTime fimExclusivo() {
        return dataFim.plusDays(1).atStartOfDay();
    }

    public long dias() {
        return ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
    }

    /** Período imediatamente anterior, com a mesma duração e os mesmos demais filtros. */
    public FiltroDashboard periodoAnterior() {
        long dias = dias();
        return new FiltroDashboard(dataInicio.minusDays(dias), dataInicio.minusDays(1),
                status, tipoEntrega, categoria, produtoId);
    }

    /** Filtros que restringem itens (produto/categoria), e não apenas pedidos. */
    public boolean filtraItens() {
        return categoria != null || produtoId != null;
    }

    public Periodo periodo() {
        return new Periodo(dataInicio, dataFim, dias());
    }
}

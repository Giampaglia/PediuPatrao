package com.umc.pediupatrao.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

/** RF05 — valores aceitos pelos filtros, para montar os campos do dashboard. */
public record OpcoesFiltro(
        Periodo periodoPadrao,
        int limitePeriodoDias,
        LocalDateTime primeiroPedido,
        LocalDateTime ultimoPedido,
        List<Opcao> status,
        List<Opcao> tiposEntrega,
        List<String> categorias,
        List<ProdutoOpcao> produtos,
        List<Opcao> agrupamentos,
        List<Opcao> criteriosRanking) {

    public record Opcao(String valor, String descricao) {
    }

    public record ProdutoOpcao(String id, String nome, String categoria, boolean ativo) {
    }
}

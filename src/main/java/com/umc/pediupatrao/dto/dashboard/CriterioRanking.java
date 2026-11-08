package com.umc.pediupatrao.dto.dashboard;

/** Critério de ordenação do ranking de produtos mais vendidos. */
public enum CriterioRanking {

    QUANTIDADE("Quantidade vendida"),
    FATURAMENTO("Faturamento");

    private final String descricao;

    CriterioRanking(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}

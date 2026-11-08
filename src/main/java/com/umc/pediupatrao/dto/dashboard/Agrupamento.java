package com.umc.pediupatrao.dto.dashboard;

/** Como as vendas são agrupadas na série do endpoint /api/dashboard/vendas. */
public enum Agrupamento {

    DIA("Por dia"),
    SEMANA("Por semana (segunda a domingo)"),
    MES("Por mês"),
    HORA_DO_DIA("Por hora do dia (horários de pico)"),
    DIA_DA_SEMANA("Por dia da semana");

    private final String descricao;

    Agrupamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}

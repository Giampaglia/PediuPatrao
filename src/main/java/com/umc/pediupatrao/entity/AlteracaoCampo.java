package com.umc.pediupatrao.entity;

/** Campo alterado em uma operação auditada, com valor anterior e novo. Imutável. */
public class AlteracaoCampo {

    private final String campo;
    private final String valorAnterior;
    private final String valorNovo;

    public AlteracaoCampo(String campo, String valorAnterior, String valorNovo) {
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
    }

    public String getCampo() {
        return campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNovo() {
        return valorNovo;
    }
}

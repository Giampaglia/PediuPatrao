package com.umc.pediupatrao.entity;

import java.util.Optional;

/**
 * Perfis de acesso do sistema. O nome do enum é usado como role no Spring
 * Security (ROLE_ADMIN, ROLE_GERENTE, ROLE_ATENDENTE).
 */
public enum Perfil {

    ADMIN("Administrador", "bg-danger"),
    GERENTE("Gerente", "bg-warning text-dark"),
    ATENDENTE("Atendente", "bg-info text-dark");

    private final String descricao;
    private final String cor;

    Perfil(String descricao, String cor) {
        this.descricao = descricao;
        this.cor = cor;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getCor() {
        return cor;
    }

    /**
     * Converte um texto salvo no banco em perfil. O valor legado "USER" passa a
     * ser tratado como ATENDENTE; valores desconhecidos não geram perfil.
     */
    public static Optional<Perfil> deTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        String normalizado = valor.trim().toUpperCase();
        if (normalizado.startsWith("ROLE_")) {
            normalizado = normalizado.substring(5);
        }
        if (normalizado.equals("USER")) {
            return Optional.of(ATENDENTE);
        }
        for (Perfil perfil : values()) {
            if (perfil.name().equals(normalizado)) {
                return Optional.of(perfil);
            }
        }
        return Optional.empty();
    }
}

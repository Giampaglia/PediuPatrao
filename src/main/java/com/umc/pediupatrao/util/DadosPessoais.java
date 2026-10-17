package com.umc.pediupatrao.util;

/** Minimização de dados pessoais exibidos/gravados na auditoria. */
public final class DadosPessoais {

    private DadosPessoais() {
    }

    /**
     * Mantém o DDD e os 4 últimos dígitos: "(11) 98765-4321" vira "(11) *****-4321".
     * Suficiente para identificar a alteração sem expor o número completo.
     */
    public static String mascararTelefone(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return null;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() < 6) {
            return "****";
        }
        String ddd = digitos.substring(0, 2);
        String finais = digitos.substring(digitos.length() - 4);
        String meio = "*".repeat(digitos.length() - 6);
        return "(" + ddd + ") " + meio + "-" + finais;
    }
}

package com.umc.pediupatrao.entity;

public enum TipoOperacao {

    INCLUSAO_PEDIDO("Inclusão de pedido"),
    ALTERACAO_STATUS_PEDIDO("Alteração de status do pedido"),
    APLICACAO_DESCONTO("Aplicação de desconto"),
    CANCELAMENTO_PEDIDO("Cancelamento de pedido"),

    INCLUSAO_CLIENTE("Inclusão de cliente"),
    ALTERACAO_CLIENTE("Alteração de cliente"),
    EXCLUSAO_CLIENTE("Exclusão de cliente"),

    INCLUSAO_USUARIO("Inclusão de usuário"),
    ALTERACAO_USUARIO("Alteração de usuário"),
    EXCLUSAO_USUARIO("Exclusão de usuário"),

    ACESSO_NEGADO("Tentativa de acesso negada");

    private final String descricao;

    TipoOperacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}

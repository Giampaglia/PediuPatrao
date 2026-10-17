package com.umc.pediupatrao.entity;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Estados do pedido e transições permitidas.
 *
 * RECEBIDO -> EM_PREPARACAO -> PRONTO -> SAIU_PARA_ENTREGA | RETIRADO -> FINALIZADO
 *
 * CANCELADO é excepcional: só pode ser alcançado pela operação de cancelamento
 * (exclusiva do GERENTE) e apenas antes da saída do pedido.
 */
public enum StatusPedido {

    RECEBIDO("Recebido", "bg-secondary"),
    EM_PREPARACAO("Em preparação", "bg-warning text-dark"),
    PRONTO("Pronto", "bg-primary"),
    SAIU_PARA_ENTREGA("Saiu para entrega", "bg-info text-dark"),
    RETIRADO("Retirado", "bg-info text-dark"),
    FINALIZADO("Finalizado", "bg-success"),
    CANCELADO("Cancelado", "bg-danger");

    private final String descricao;
    private final String cor;

    StatusPedido(String descricao, String cor) {
        this.descricao = descricao;
        this.cor = cor;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getCor() {
        return cor;
    }

    /** Próximos estados do fluxo normal (sem o cancelamento). */
    public Set<StatusPedido> proximos(TipoEntrega tipoEntrega) {
        return switch (this) {
            case RECEBIDO -> EnumSet.of(EM_PREPARACAO);
            case EM_PREPARACAO -> EnumSet.of(PRONTO);
            case PRONTO -> {
                if (tipoEntrega == TipoEntrega.ENTREGA) {
                    yield EnumSet.of(SAIU_PARA_ENTREGA);
                }
                if (tipoEntrega == TipoEntrega.RETIRADA) {
                    yield EnumSet.of(RETIRADO);
                }
                yield EnumSet.of(SAIU_PARA_ENTREGA, RETIRADO);
            }
            case SAIU_PARA_ENTREGA, RETIRADO -> EnumSet.of(FINALIZADO);
            case FINALIZADO, CANCELADO -> EnumSet.noneOf(StatusPedido.class);
        };
    }

    public boolean podeIrPara(StatusPedido destino, TipoEntrega tipoEntrega) {
        return destino != null && proximos(tipoEntrega).contains(destino);
    }

    /** Estados que representam a saída do pedido do estabelecimento. */
    public boolean isSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    /** Desconto e cancelamento só são permitidos antes da saída. */
    public boolean isAntesDaSaida() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    public boolean isEncerrado() {
        return this == FINALIZADO || this == CANCELADO;
    }

    /**
     * Converte texto em status aceitando também os valores usados na versão
     * anterior do sistema (EM_PREPARO, ENTREGUE...).
     */
    public static Optional<StatusPedido> deTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        String normalizado = valor.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        for (StatusPedido status : values()) {
            if (status.name().equals(normalizado)) {
                return Optional.of(status);
            }
        }
        return switch (normalizado) {
            case "EM_PREPARO", "PREPARANDO" -> Optional.of(EM_PREPARACAO);
            case "ENTREGUE", "CONCLUIDO" -> Optional.of(FINALIZADO);
            case "PENDENTE", "NOVO" -> Optional.of(RECEBIDO);
            default -> Optional.empty();
        };
    }
}

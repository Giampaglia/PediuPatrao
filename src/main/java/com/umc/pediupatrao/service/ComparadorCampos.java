package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Monta a lista de campos alterados (valor anterior x novo) para a auditoria. */
public class ComparadorCampos {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final List<AlteracaoCampo> alteracoes = new ArrayList<>();

    /** Registra o campo somente se o valor mudou. */
    public ComparadorCampos comparar(String campo, Object anterior, Object novo) {
        String textoAnterior = texto(anterior);
        String textoNovo = texto(novo);
        if (!Objects.equals(textoAnterior, textoNovo)) {
            alteracoes.add(new AlteracaoCampo(campo, textoAnterior, textoNovo));
        }
        return this;
    }

    /** Registra o campo sempre (útil em inclusões). Valores vazios são ignorados. */
    public ComparadorCampos registrar(String campo, Object valor) {
        String texto = texto(valor);
        if (texto != null) {
            alteracoes.add(new AlteracaoCampo(campo, null, texto));
        }
        return this;
    }

    public List<AlteracaoCampo> lista() {
        return List.copyOf(alteracoes);
    }

    public boolean isVazio() {
        return alteracoes.isEmpty();
    }

    static String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof String s) {
            return s.isBlank() ? null : s.trim();
        }
        if (valor instanceof BigDecimal b) {
            return b.stripTrailingZeros().toPlainString();
        }
        if (valor instanceof LocalDateTime d) {
            return d.format(FORMATO_DATA);
        }
        if (valor instanceof Enum<?> e) {
            return e.name();
        }
        return valor.toString();
    }
}

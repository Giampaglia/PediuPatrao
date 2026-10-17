package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

/** Desconto percentual (0,01 a 20) e motivo opcional. */
public record DescontoRequest(BigDecimal percentual, String motivo) {
}

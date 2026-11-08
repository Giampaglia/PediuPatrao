package com.umc.pediupatrao.dto.dashboard;

import java.time.LocalDate;

public record Periodo(LocalDate dataInicio, LocalDate dataFim, long dias) {
}

package com.umc.pediupatrao.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Relógio injetável: os testes fixam "hoje" para que os períodos padrão sejam previsíveis. */
@Configuration
public class RelogioConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}

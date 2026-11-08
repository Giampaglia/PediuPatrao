package com.umc.pediupatrao.config;

import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Limites de tempo do driver do MongoDB. O padrão do driver espera 30 s para
 * encontrar o servidor; com o banco fora do ar, cada endpoint do dashboard
 * (que faz várias consultas) ficaria minutos sem responder. Com limites
 * curtos, a API devolve 503 rapidamente e o dashboard avisa o usuário (RF07).
 */
@Configuration
public class MongoTimeoutConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer limitesDeTempoMongo(
            @Value("${pediupatrao.mongodb.timeout-selecao-ms:5000}") int selecaoMs,
            @Value("${pediupatrao.mongodb.timeout-conexao-ms:5000}") int conexaoMs,
            @Value("${pediupatrao.mongodb.timeout-leitura-ms:15000}") int leituraMs) {
        return builder -> builder
                .applyToClusterSettings(c -> c.serverSelectionTimeout(selecaoMs, TimeUnit.MILLISECONDS))
                .applyToSocketSettings(s -> s
                        .connectTimeout(conexaoMs, TimeUnit.MILLISECONDS)
                        .readTimeout(leituraMs, TimeUnit.MILLISECONDS));
    }
}

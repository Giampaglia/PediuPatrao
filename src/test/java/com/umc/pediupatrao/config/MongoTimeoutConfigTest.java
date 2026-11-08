package com.umc.pediupatrao.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mongodb.MongoClientSettings;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** RF07 — com o banco fora do ar, a API deve falhar rápido (503) em vez de travar. */
class MongoTimeoutConfigTest {

    @Test
    void aplicaLimitesDeTempoAoDriver() {
        MongoClientSettings.Builder builder = MongoClientSettings.builder();
        new MongoTimeoutConfig().limitesDeTempoMongo(5000, 4000, 15000).customize(builder);
        MongoClientSettings settings = builder.build();

        assertEquals(5000, settings.getClusterSettings().getServerSelectionTimeout(TimeUnit.MILLISECONDS));
        assertEquals(4000, settings.getSocketSettings().getConnectTimeout(TimeUnit.MILLISECONDS));
        assertEquals(15000, settings.getSocketSettings().getReadTimeout(TimeUnit.MILLISECONDS));
    }
}

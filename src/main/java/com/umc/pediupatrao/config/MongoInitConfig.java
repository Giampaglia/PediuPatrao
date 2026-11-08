package com.umc.pediupatrao.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

@Configuration
public class MongoInitConfig implements CommandLineRunner {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) throws Exception {
        if (!mongoTemplate.collectionExists("pedidos")) {
            mongoTemplate.createCollection("pedidos");
        }
        if (!mongoTemplate.collectionExists("clientes")) {
            mongoTemplate.createCollection("clientes");
        }
        if (!mongoTemplate.collectionExists("usuarios")) {
            mongoTemplate.createCollection("usuarios");
        }
        if (!mongoTemplate.collectionExists("produtos")) {
            mongoTemplate.createCollection("produtos");
        }
        if (!mongoTemplate.collectionExists("horariosAtendimento")) {
            mongoTemplate.createCollection("horariosAtendimento");
        }
        if (!mongoTemplate.collectionExists("auditoria")) {
            mongoTemplate.createCollection("auditoria");
        }
        criarIndicesDoDashboard();
    }

    /**
     * Índices usados pelas consultas do dashboard: todas filtram pelo período
     * (data de entrada) e, opcionalmente, por status. Criar um índice que já
     * existe não tem efeito, então é seguro executar a cada inicialização.
     */
    private void criarIndicesDoDashboard() {
        IndexOperations pedidos = mongoTemplate.indexOps("pedidos");
        pedidos.ensureIndex(new Index().on("dataHoraEntrada", Sort.Direction.DESC)
                .named("idx_pedidos_dataHoraEntrada"));
        pedidos.ensureIndex(new Index().on("status", Sort.Direction.ASC).on("dataHoraEntrada", Sort.Direction.DESC)
                .named("idx_pedidos_status_dataHoraEntrada"));
        pedidos.ensureIndex(new Index().on("itens.produtoId", Sort.Direction.ASC)
                .named("idx_pedidos_itens_produtoId"));
    }
}

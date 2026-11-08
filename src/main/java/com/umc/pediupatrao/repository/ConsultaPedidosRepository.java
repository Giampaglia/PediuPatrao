package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * Consultas de leitura do dashboard sobre a coleção "pedidos".
 *
 * Os filtros (período, status, tipo de entrega e produto) são aplicados no
 * MongoDB, usando os índices criados em MongoInitConfig; somente os pedidos do
 * recorte chegam à aplicação, onde os indicadores são calculados
 * (CalculadoraIndicadores). O histórico de status não é carregado porque não é
 * usado pelos indicadores.
 */
@Repository
public class ConsultaPedidosRepository {

    static final String COLECAO = "pedidos";

    /**
     * Valores gravados pela versão anterior do sistema que equivalem a cada status
     * (ver StatusPedido.deTexto). Sem isso, filtrar por FINALIZADO ignoraria
     * pedidos antigos gravados como ENTREGUE.
     */
    private static final Map<StatusPedido, List<String>> VALORES_LEGADOS = Map.of(
            StatusPedido.RECEBIDO, List.of("PENDENTE", "NOVO"),
            StatusPedido.EM_PREPARACAO, List.of("EM_PREPARO", "PREPARANDO"),
            StatusPedido.FINALIZADO, List.of("ENTREGUE", "CONCLUIDO"));

    private final MongoTemplate mongoTemplate;

    public ConsultaPedidosRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Pedidos com entrada no período do filtro.
     *
     * @param produtoIds produtos que o pedido deve conter (filtro de produto/categoria);
     *                   null = sem restrição; vazio = nenhum produto atende ao filtro
     */
    public List<Pedido> buscar(FiltroDashboard filtro, Collection<String> produtoIds) {
        if (produtoIds != null && produtoIds.isEmpty()) {
            return List.of();
        }
        Query query = new Query(criterios(filtro, produtoIds));
        query.fields().exclude("historico");
        return mongoTemplate.find(query, Pedido.class, COLECAO);
    }

    /** Pedidos mais recentes do recorte, do mais novo para o mais antigo. */
    public List<Pedido> buscarRecentes(FiltroDashboard filtro, Collection<String> produtoIds, int limite) {
        if (produtoIds != null && produtoIds.isEmpty()) {
            return List.of();
        }
        Query query = new Query(criterios(filtro, produtoIds))
                .with(Sort.by(Sort.Direction.DESC, "dataHoraEntrada"))
                .limit(limite);
        query.fields().exclude("historico");
        return mongoTemplate.find(query, Pedido.class, COLECAO);
    }

    /** Pedidos antigos sem data de entrada: não entram em nenhum período (RF07). */
    public long contarSemDataEntrada() {
        return mongoTemplate.count(new Query(Criteria.where("dataHoraEntrada").is(null)), COLECAO);
    }

    public Optional<LocalDateTime> primeiraEntrada() {
        return entradaExtrema(Sort.Direction.ASC);
    }

    public Optional<LocalDateTime> ultimaEntrada() {
        return entradaExtrema(Sort.Direction.DESC);
    }

    private Optional<LocalDateTime> entradaExtrema(Sort.Direction direcao) {
        Query query = new Query(Criteria.where("dataHoraEntrada").ne(null))
                .with(Sort.by(direcao, "dataHoraEntrada"))
                .limit(1);
        query.fields().include("dataHoraEntrada");
        return Optional.ofNullable(mongoTemplate.findOne(query, Pedido.class, COLECAO))
                .map(Pedido::getDataHoraEntrada);
    }

    static Criteria criterios(FiltroDashboard filtro, Collection<String> produtoIds) {
        Criteria criteria = Criteria.where("dataHoraEntrada").gte(filtro.inicio()).lt(filtro.fimExclusivo());
        if (!filtro.status().isEmpty()) {
            criteria = criteria.and("status").in(valoresDeStatus(filtro.status()));
        }
        if (filtro.tipoEntrega() != null) {
            criteria = criteria.and("tipoEntrega").is(filtro.tipoEntrega().name());
        }
        if (produtoIds != null) {
            criteria = criteria.and("itens.produtoId").in(produtoIds);
        }
        return criteria;
    }

    static List<String> valoresDeStatus(Collection<StatusPedido> status) {
        List<String> valores = new ArrayList<>();
        for (StatusPedido s : status) {
            valores.add(s.name());
            valores.addAll(VALORES_LEGADOS.getOrDefault(s, List.of()));
        }
        return valores;
    }
}

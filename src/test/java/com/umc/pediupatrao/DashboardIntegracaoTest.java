package com.umc.pediupatrao;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Teste de integração ponta a ponta: API -> serviço -> MongoDB (embarcado,
 * via Flapdoodle). Os pedidos são gravados no banco e os filtros do dashboard
 * são executados pelas consultas reais do ConsultaPedidosRepository.
 */
@SpringBootTest(properties = {
        "de.flapdoodle.mongodb.embedded.version=7.0.14",
        "spring.data.mongodb.database=pediupatrao_teste",
        "logging.level.de.flapdoodle=WARN",
        "logging.level.org.mongodb.driver=WARN"
})
@AutoConfigureMockMvc
@WithMockUser(username = "gerente01", roles = "GERENTE")
class DashboardIntegracaoTest {

    private static final LocalDate HOJE = LocalDate.now();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PedidoRepository pedidoRepository;
    @Autowired
    private ProdutoRepository produtoRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    private Produto pizza;
    private Produto refri;

    @BeforeEach
    void prepararBanco() {
        pedidoRepository.deleteAll();
        produtoRepository.deleteAll();
        pizza = produtoRepository.save(produto("Pizza Calabresa", "Pizza", 50.00));
        refri = produtoRepository.save(produto("Refrigerante 2L", "Bebida", 10.00));

        LocalDateTime ontem = HOJE.minusDays(1).atTime(19, 0);
        pedidoRepository.saveAll(List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, ontem, item(pizza, 2), item(refri, 1)),   // 110
                pedido(StatusPedido.FINALIZADO, TipoEntrega.RETIRADA, ontem.plusHours(1), item(pizza, 1)),     // 50
                pedido(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA, HOJE.atTime(12, 0), item(refri, 3)),   // 30
                pedido(StatusPedido.CANCELADO, TipoEntrega.ENTREGA, HOJE.atTime(12, 30), item(pizza, 4)),      // 200
                // fora do período padrão (40 dias atrás)
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.minusDays(40).atTime(20, 0), item(pizza, 10))));

        // Documento gravado pela versão anterior do sistema: status "ENTREGUE" e sem itens
        Document legado = new Document("clienteNome", "Cliente antigo")
                .append("status", "ENTREGUE")
                .append("tipoEntrega", "RETIRADA")
                .append("valorTotal", "25.00")
                .append("dataHoraEntrada", java.sql.Timestamp.valueOf(HOJE.minusDays(2).atTime(18, 0)));
        mongoTemplate.getCollection("pedidos").insertOne(legado);
        // Documento antigo sem data de entrada: não pertence a nenhum período
        mongoTemplate.getCollection("pedidos").insertOne(new Document("clienteNome", "Sem data").append("status", "ENTREGUE"));
    }

    @Test
    void resumoCalculaOsIndicadoresAPartirDoBanco() throws Exception {
        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPedidos").value(5))
                .andExpect(jsonPath("$.pedidosValidos").value(4))
                .andExpect(jsonPath("$.pedidosCancelados").value(1))
                .andExpect(jsonPath("$.faturamento").value(215.00))   // 110 + 50 + 30 + 25 (legado)
                .andExpect(jsonPath("$.pedidosEmAndamento").value(1))
                .andExpect(jsonPath("$.qualidadeDados.pedidosSemDataEntrada").value(1))
                .andExpect(jsonPath("$.comparativo.faturamento").value(500.00)); // pedido de 40 dias atrás
    }

    @Test
    void filtroDeStatusEhAplicadoNoBancoInclusiveComValoresLegados() throws Exception {
        mvc.perform(get("/api/dashboard/pedidos/status").param("status", "FINALIZADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))          // 2 finalizados + 1 legado "ENTREGUE"
                .andExpect(jsonPath("$.finalizados").value(3));
    }

    @Test
    void filtrosDeTipoDeEntregaEPeriodo() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").param("tipoEntrega", "ENTREGA"))
                .andExpect(jsonPath("$.totalPedidos").value(3));
        mvc.perform(get("/api/dashboard/resumo")
                        .param("dataInicio", HOJE.toString()).param("dataFim", HOJE.toString()))
                .andExpect(jsonPath("$.totalPedidos").value(2))
                .andExpect(jsonPath("$.periodo.dias").value(1));
    }

    @Test
    void filtroDeCategoriaRestringePedidosEItens() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").param("categoria", "Bebida"))
                .andExpect(jsonPath("$.totalPedidos").value(2))      // pedidos que contêm bebida
                .andExpect(jsonPath("$.faturamento").value(40.00))   // só os itens de bebida
                .andExpect(jsonPath("$.itensVendidos").value(4));
        mvc.perform(get("/api/dashboard/produtos/mais-vendidos").param("produtoId", pizza.getId()))
                .andExpect(jsonPath("$.produtos", hasSize(1)))
                .andExpect(jsonPath("$.produtos[0].quantidade").value(3)); // cancelado não entra
    }

    @Test
    void rankingCategoriasERecentes() throws Exception {
        mvc.perform(get("/api/dashboard/produtos/mais-vendidos"))
                .andExpect(jsonPath("$.produtos[0].nome").value("Refrigerante 2L"))   // 4 unidades
                .andExpect(jsonPath("$.produtos[1].nome").value("Pizza Calabresa"));  // 3 unidades
        mvc.perform(get("/api/dashboard/vendas/categorias"))
                .andExpect(jsonPath("$.categorias[0].categoria").value("Pizza"))
                .andExpect(jsonPath("$.categorias[0].faturamento").value(150.00));
        mvc.perform(get("/api/dashboard/pedidos/recentes").param("limite", "2"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("CANCELADO"))       // 12:30 de hoje
                .andExpect(jsonPath("$[1].status").value("EM_PREPARACAO"));  // 12:00 de hoje
    }

    @Test
    void vendasPorDiaPreencheOPeriodoPadrao() throws Exception {
        mvc.perform(get("/api/dashboard/vendas"))
                .andExpect(jsonPath("$.pontos", hasSize(30)))
                .andExpect(jsonPath("$.pontos[28].pedidos").value(2))   // ontem
                .andExpect(jsonPath("$.pontos[29].pedidos").value(1));  // hoje (o cancelado não conta)
    }

    @Test
    void filtrosDisponiveisVemDoCadastroEDosPedidos() throws Exception {
        mvc.perform(get("/api/dashboard/filtros"))
                .andExpect(jsonPath("$.categorias", hasSize(2)))
                .andExpect(jsonPath("$.produtos", hasSize(2)))
                .andExpect(jsonPath("$.primeiroPedido").exists());
    }

    @Test
    void indicesDoDashboardSaoCriadosNaInicializacao() {
        Set<String> nomes = mongoTemplate.indexOps("pedidos").getIndexInfo().stream()
                .map(IndexInfo::getName).collect(Collectors.toSet());
        assertTrue(nomes.containsAll(Set.of("idx_pedidos_dataHoraEntrada", "idx_pedidos_status_dataHoraEntrada",
                "idx_pedidos_itens_produtoId")), "Índices encontrados: " + nomes);
    }

    private static Produto produto(String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        return p;
    }

    private static ItemPedido item(Produto produto, int quantidade) {
        return ItemPedido.de(produto, quantidade);
    }

    private static Pedido pedido(StatusPedido status, TipoEntrega tipo, LocalDateTime entrada, ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setClienteNome("Cliente Teste");
        p.setStatus(status);
        p.setTipoEntrega(tipo);
        p.setDataHoraEntrada(entrada);
        p.setItens(new ArrayList<>(List.of(itens)));
        p.recalcularValores();
        return p;
    }
}

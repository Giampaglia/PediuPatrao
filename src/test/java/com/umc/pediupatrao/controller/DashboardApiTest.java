package com.umc.pediupatrao.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.repository.ConsultaPedidosRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.AcessoNegadoHandler;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.dashboard.DashboardService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testa a API do dashboard pela camada HTTP com o DashboardService real
 * (apenas os repositórios são simulados): permissões, validação dos filtros,
 * códigos de erro e formato do JSON.
 */
@WebMvcTest(controllers = {DashboardController.class, DashboardPageController.class})
@Import({SecurityConfig.class, AcessoNegadoHandler.class, DashboardService.class, DashboardApiTest.RelogioFixo.class})
class DashboardApiTest {

    static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    static final LocalDate HOJE = LocalDate.of(2026, 11, 8);

    @TestConfiguration
    static class RelogioFixo {
        @Bean
        Clock clock() {
            return Clock.fixed(ZonedDateTime.of(HOJE.atTime(20, 0), ZONA).toInstant(), ZONA);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConsultaPedidosRepository consulta;
    @MockitoBean
    private ProdutoRepository produtoRepository;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @BeforeEach
    void setUp() {
        Produto pizza = new Produto();
        pizza.setId("pizza");
        pizza.setNome("Pizza Calabresa");
        pizza.setTipo("Pizza");
        pizza.setPreco(50.0);
        pizza.setAtivo(true);

        Pedido finalizado = pedido(StatusPedido.FINALIZADO, pizza, 2, HOJE.atTime(19, 0));
        Pedido cancelado = pedido(StatusPedido.CANCELADO, pizza, 1, HOJE.atTime(19, 30));

        when(produtoRepository.findAll()).thenReturn(List.of(pizza));
        when(consulta.buscar(any(), any())).thenReturn(List.of(finalizado, cancelado));
        when(consulta.buscarRecentes(any(), any(), anyInt())).thenReturn(List.of(cancelado, finalizado));
        when(consulta.primeiraEntrada()).thenReturn(Optional.of(HOJE.atTime(19, 0)));
        when(consulta.ultimaEntrada()).thenReturn(Optional.of(HOJE.atTime(19, 30)));
    }

    // ===== Segurança =====

    @Test
    void anonimoRecebe401() throws Exception {
        mvc.perform(get("/api/dashboard/resumo")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoAcessaODashboard() throws Exception {
        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/dashboard")).andExpect(status().isForbidden());
        verify(consulta, never()).buscar(any(), any());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void apiEhSomenteLeitura() throws Exception {
        mvc.perform(post("/api/dashboard/resumo").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAcessaATelaDoDashboard() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"dashboard\"")))
                .andExpect(content().string(containsString("/js/dashboard.js")));
    }

    // ===== Endpoints =====

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void resumoRetornaIndicadores() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").param("dataInicio", "2026-11-01").param("dataFim", "2026-11-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo.dataInicio").value("2026-11-01"))
                .andExpect(jsonPath("$.periodo.dias").value(8))
                .andExpect(jsonPath("$.faturamento").value(100.00))
                .andExpect(jsonPath("$.pedidosValidos").value(1))
                .andExpect(jsonPath("$.pedidosCancelados").value(1))
                .andExpect(jsonPath("$.taxaCancelamento").value(50.0))
                .andExpect(jsonPath("$.ticketMedio").value(100.00))
                .andExpect(jsonPath("$.comparativo.periodoAnterior.dataFim").value("2026-10-31"))
                .andExpect(jsonPath("$.qualidadeDados.pedidosSemStatus").value(0));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void vendasPorHoraRetorna24Pontos() throws Exception {
        mvc.perform(get("/api/dashboard/vendas").param("agrupamento", "HORA_DO_DIA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agrupamento").value("HORA_DO_DIA"))
                .andExpect(jsonPath("$.pontos", hasSize(24)))
                .andExpect(jsonPath("$.pontos[19].pedidos").value(1));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void vendasPorDiaEhOPadrao() throws Exception {
        mvc.perform(get("/api/dashboard/vendas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agrupamento").value("DIA"))
                .andExpect(jsonPath("$.pontos", hasSize(30)));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void demaisEndpointsRespondem() throws Exception {
        mvc.perform(get("/api/dashboard/pedidos/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porStatus", hasSize(StatusPedido.values().length)))
                .andExpect(jsonPath("$.cancelados").value(1));
        mvc.perform(get("/api/dashboard/pedidos/recentes").param("limite", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CANCELADO"))
                .andExpect(jsonPath("$[0].statusDescricao").value("Cancelado"));
        mvc.perform(get("/api/dashboard/produtos/mais-vendidos").param("ordenarPor", "FATURAMENTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criterio").value("FATURAMENTO"))
                .andExpect(jsonPath("$.produtos[0].nome").value("Pizza Calabresa"))
                .andExpect(jsonPath("$.produtos[0].quantidade").value(2));
        mvc.perform(get("/api/dashboard/vendas/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias[0].categoria").value("Pizza"));
        mvc.perform(get("/api/dashboard/vendas/tipos-entrega"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipos", hasSize(2)));
        mvc.perform(get("/api/dashboard/filtros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias[0]").value("Pizza"))
                .andExpect(jsonPath("$.limitePeriodoDias").value(366))
                .andExpect(jsonPath("$.periodoPadrao.dataFim").value("2026-11-08"));
    }

    // ===== Erros =====

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void filtrosInvalidosRetornam400ComMensagem() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").param("dataInicio", "2026-11-10").param("dataFim", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value(containsString("não pode ser posterior")))
                .andExpect(jsonPath("$.caminho").value("/api/dashboard/resumo"));
        mvc.perform(get("/api/dashboard/resumo").param("dataInicio", "ontem"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("AAAA-MM-DD")));
        mvc.perform(get("/api/dashboard/resumo").param("status", "VOANDO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("Valores aceitos")));
        mvc.perform(get("/api/dashboard/vendas").param("agrupamento", "ANO"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/pedidos/recentes").param("limite", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("limite")));
        mvc.perform(get("/api/dashboard/produtos/mais-vendidos").param("limite", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("entre 1 e 50")));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void bancoIndisponivelRetorna503() throws Exception {
        when(consulta.buscar(any(), any())).thenThrow(new DataAccessResourceFailureException("timeout"));

        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.mensagem").value(containsString("Tente novamente")));
    }

    private static Pedido pedido(StatusPedido status, Produto produto, int quantidade, LocalDateTime entrada) {
        Pedido p = new Pedido();
        p.setId("665f1c2ab4d3e9a1b2c3d4e" + quantidade);
        p.setClienteNome("Maria Souza");
        p.setStatus(status);
        p.setTipoEntrega(TipoEntrega.ENTREGA);
        p.setDataHoraEntrada(entrada);
        p.setItens(new ArrayList<>(List.of(ItemPedido.de(produto, quantidade))));
        p.recalcularValores();
        return p;
    }
}

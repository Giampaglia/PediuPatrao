package com.umc.pediupatrao.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.security.AcessoNegadoHandler;
import com.umc.pediupatrao.security.UsuarioLogado;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Renderiza as telas Thymeleaf com dados simulados e verifica as restrições por perfil. */
@WebMvcTest(controllers = {PedidoPageController.class, AuditoriaPageController.class, HomeController.class})
@Import({SecurityConfig.class, AcessoNegadoHandler.class})
class PaginasTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoService pedidoService;
    @MockitoBean
    private ClienteService clienteService;
    @MockitoBean
    private ProdutoService produtoService;
    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;
    @MockitoBean
    private UsuarioLogado usuarioLogado;

    private Pedido pedido;

    @BeforeEach
    void setUp() {
        Produto pizza = new Produto();
        pizza.setId("pz");
        pizza.setNome("Pizza Calabresa");
        pizza.setPreco(49.90);
        pizza.setAtivo(true);

        pedido = new Pedido();
        pedido.setId("665f1c2ab4d3e9a1b2c3d4e5");
        pedido.setClienteNome("Maria Souza");
        pedido.setTipoEntrega(TipoEntrega.ENTREGA);
        pedido.setItens(new ArrayList<>(List.of(ItemPedido.de(pizza, 2))));
        pedido.recalcularValores();
        pedido.setStatus(StatusPedido.PRONTO);
        pedido.setDataHoraEntrada(LocalDateTime.now());
        pedido.setResponsavelEntrada("atendente01");
        pedido.registrarHistorico(StatusPedido.RECEBIDO, LocalDateTime.now(), "atendente01");

        Pedido legado = new Pedido(); // documento antigo, sem itens/valores/status
        legado.setId("antigo");
        legado.setItens(null);
        legado.setHistorico(null);
        legado.setSubtotal(null);
        legado.setValorTotal(null);
        legado.setPercentualDesconto(null);

        Cliente cliente = new Cliente();
        cliente.setId("c1");
        cliente.setNome("Maria Souza");
        cliente.setTelefone("(11) 98765-4321");

        RegistroAuditoria comCampos = new RegistroAuditoria("a1", "atendente01", "ATENDENTE", LocalDateTime.now(),
                TipoOperacao.ALTERACAO_CLIENTE, "CLIENTE", "c1",
                List.of(new AlteracaoCampo("logradouro", "Rua das Flores", "Av. Brasil"),
                        new AlteracaoCampo("numero", "10", "20")), null);
        RegistroAuditoria semCampos = new RegistroAuditoria("a2", "atendente01", "ATENDENTE", LocalDateTime.now(),
                TipoOperacao.ACESSO_NEGADO, "SISTEMA", "POST /api/pedidos/1/cancelar", List.of(), "Perfil sem permissão");

        when(pedidoService.listarPedidos()).thenReturn(List.of(pedido, legado));
        when(pedidoService.buscarPorId(pedido.getId())).thenReturn(pedido);
        when(clienteService.listarClientes()).thenReturn(List.of(cliente));
        when(produtoService.listarProdutos()).thenReturn(List.of(pizza));
        when(auditoriaService.consultar(any(), any(), any(), any())).thenReturn(List.of(comCampos, semCampos));
        when(auditoriaService.historicoDe(any(), any())).thenReturn(List.of(comCampos));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listaDePedidosRenderizaInclusiveDocumentosAntigos() throws Exception {
        mvc.perform(get("/pedidos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Maria Souza")))
                .andExpect(content().string(containsString("R$ 99,80")))
                .andExpect(content().string(not(containsString("Novo Pedido"))));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void detalheMostraAcoesDoGerenteEAuditoria() throws Exception {
        when(usuarioLogado.temPerfil(Perfil.GERENTE)).thenReturn(true);
        mvc.perform(get("/pedidos/" + pedido.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Marcar como: Saiu para entrega")))
                .andExpect(content().string(containsString("Aplicar desconto")))
                .andExpect(content().string(containsString("Cancelar pedido")))
                .andExpect(content().string(containsString("Auditoria deste pedido")));
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void detalheParaAtendenteNaoMostraDescontoNemCancelamento() throws Exception {
        mvc.perform(get("/pedidos/" + pedido.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Marcar como: Saiu para entrega")))
                .andExpect(content().string(not(containsString("Aplicar desconto"))))
                .andExpect(content().string(not(containsString("Auditoria deste pedido"))));
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void formularioDeNovoPedidoRenderiza() throws Exception {
        mvc.perform(get("/pedidos/novo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pizza Calabresa")))
                .andExpect(content().string(containsString("Retirada no balcão")));
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoCancelaPelaTela() throws Exception {
        mvc.perform(post("/pedidos/" + pedido.getId() + "/cancelar").with(csrf()).param("justificativa", "Cliente desistiu"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void auditoriaRenderizaCamposAnterioresENovos() throws Exception {
        mvc.perform(get("/auditoria"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rua das Flores")))
                .andExpect(content().string(containsString("Av. Brasil")))
                .andExpect(content().string(containsString("Tentativa de acesso negada")));
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoAbreAuditoria() throws Exception {
        mvc.perform(get("/auditoria")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void telasDeUsuarioRenderizamComOsTresPerfis() throws Exception {
        Usuario gerente = new Usuario();
        gerente.setId("u1");
        gerente.setUsername("gerente01");
        gerente.setRole(Perfil.GERENTE);
        when(usuarioService.listarTodos()).thenReturn(List.of(gerente));
        when(usuarioService.buscarPorId("u1")).thenReturn(Optional.of(gerente));
        when(usuarioLogado.username()).thenReturn("admin");

        mvc.perform(get("/usuarios")).andExpect(status().isOk())
                .andExpect(content().string(containsString("gerente01")));
        mvc.perform(get("/usuarios/editar/u1")).andExpect(status().isOk())
                .andExpect(content().string(containsString("ATENDENTE")))
                .andExpect(content().string(containsString("GERENTE")));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void outrasTelasRenderizam() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Auditoria")))
                .andExpect(content().string(containsString("_csrf")));
        mvc.perform(get("/clientes")).andExpect(status().isOk());
        mvc.perform(get("/produtos")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Pizza Calabresa")));
    }
}

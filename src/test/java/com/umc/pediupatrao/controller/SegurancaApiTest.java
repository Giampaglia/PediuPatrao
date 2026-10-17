package com.umc.pediupatrao.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.security.AcessoNegadoHandler;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Comprova que as permissões são aplicadas no backend: as requisições são
 * feitas diretamente às APIs, sem passar pelas telas.
 */
@WebMvcTest(controllers = {PedidoController.class, UsuarioController.class, AuditoriaController.class})
@Import({SecurityConfig.class, AcessoNegadoHandler.class})
class SegurancaApiTest {

    private static final String CANCELAMENTO = "{\"justificativa\":\"Cliente desistiu do pedido\"}";
    private static final String DESCONTO = "{\"percentual\":10}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoService pedidoService;
    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void anonimoRecebe401NaApi() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoPodeCancelarERegistraTentativa() throws Exception {
        mvc.perform(post("/api/pedidos/p1/cancelar").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isForbidden());
        verify(pedidoService, never()).cancelar(any(), any());
        verify(auditoriaService).registrar(eq(TipoOperacao.ACESSO_NEGADO), eq("SISTEMA"), anyString(), anyList(), anyString());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoPodeCancelar() throws Exception {
        mvc.perform(post("/api/pedidos/p1/cancelar").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoPodeAplicarDesconto() throws Exception {
        mvc.perform(post("/api/pedidos/p1/desconto").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isForbidden());
        verify(pedidoService, never()).aplicarDesconto(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerentePodeCancelar() throws Exception {
        when(pedidoService.cancelar(eq("p1"), anyString())).thenReturn(new Pedido());
        mvc.perform(post("/api/pedidos/p1/cancelar").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void requisicaoSemTokenCsrfEhBloqueada() throws Exception {
        mvc.perform(post("/api/pedidos/p1/cancelar")
                .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isForbidden());
        verify(pedidoService, never()).cancelar(any(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminApenasConsultaPedidos() throws Exception {
        when(pedidoService.listarPedidos()).thenReturn(List.of());
        mvc.perform(get("/api/pedidos")).andExpect(status().isOk());
        mvc.perform(post("/api/pedidos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void somenteAdminGerenciaUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"hacker\",\"password\":\"123456\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        verify(usuarioService, never()).salvarUsuario(any());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoConsultaAuditoria() throws Exception {
        mvc.perform(get("/api/auditoria")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteConsultaAuditoriaMasNaoExclui() throws Exception {
        mvc.perform(get("/api/auditoria")).andExpect(status().isOk());
        mvc.perform(delete("/api/auditoria/qualquer").with(csrf())).andExpect(status().isForbidden());
    }
}

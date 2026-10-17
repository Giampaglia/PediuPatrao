package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private UsuarioLogado usuarioLogado;

    private UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(usuarioRepository, passwordEncoder, auditoriaService, usuarioLogado);
        lenient().when(usuarioLogado.username()).thenReturn("admin");
        lenient().when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        lenient().when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void adminNaoPodeAlterarOProprioPerfil() {
        when(usuarioRepository.findById("u-admin")).thenReturn(Optional.of(usuario("u-admin", "admin", Perfil.ADMIN)));

        assertThrows(RegraNegocioException.class,
                () -> service.atualizarUsuario("u-admin", dados("admin", Perfil.ATENDENTE)));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void ultimoAdminNaoPodeSerRebaixado() {
        when(usuarioRepository.findById("u2")).thenReturn(Optional.of(usuario("u2", "chefe", Perfil.ADMIN)));
        when(usuarioRepository.countByRole(Perfil.ADMIN)).thenReturn(1L);

        assertThrows(RegraNegocioException.class, () -> service.atualizarUsuario("u2", dados("chefe", Perfil.GERENTE)));
    }

    @Test
    void naoPodeExcluirOProprioUsuario() {
        when(usuarioRepository.findById("u-admin")).thenReturn(Optional.of(usuario("u-admin", "admin", Perfil.ADMIN)));
        assertThrows(RegraNegocioException.class, () -> service.deletarUsuario("u-admin"));
        verify(usuarioRepository, never()).deleteById(any());
    }

    @Test
    void usernameDuplicadoEhRejeitado() {
        when(usuarioRepository.findByUsername("gerente01")).thenReturn(Optional.of(usuario("x", "gerente01", Perfil.GERENTE)));
        Usuario dados = dados("gerente01", Perfil.GERENTE);
        dados.setPassword("segredo123");
        assertThrows(RegraNegocioException.class, () -> service.salvarUsuario(dados));
    }

    @Test
    void criacaoIgnoraIdEnviadoCriptografaSenhaENaoAuditaSenha() {
        Usuario dados = dados("novo.atendente", Perfil.ATENDENTE);
        dados.setId("id-de-outro-usuario");
        dados.setPassword("segredo123");

        Usuario salvo = service.salvarUsuario(dados);

        assertNull(salvo.getId());
        assertEquals("hash:segredo123", salvo.getPassword());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AlteracaoCampo>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_USUARIO), eq("USUARIO"), isNull(), captor.capture(), isNull());
        assertTrue(captor.getValue().stream().noneMatch(a -> a.getCampo().contains("senha")
                || String.valueOf(a.getValorNovo()).contains("segredo123")));
    }

    private static Usuario usuario(String id, String username, Perfil perfil) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setPassword("hash");
        u.setRole(perfil);
        return u;
    }

    private static Usuario dados(String username, Perfil perfil) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setRole(perfil);
        return u;
    }
}

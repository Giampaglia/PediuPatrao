package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private AuditoriaService auditoriaService;

    private ClienteService service;

    @BeforeEach
    void setUp() {
        service = new ClienteService(clienteRepository, auditoriaService);
        lenient().when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void alteracaoDeEnderecoRegistraValorAnteriorENovo() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("Rua das Flores", "(11) 98765-4321")));

        service.salvar(cliente("Av. Brasil", "(11) 98765-4321"));

        List<AlteracaoCampo> alteracoes = capturar();
        assertEquals(1, alteracoes.size());
        assertEquals("logradouro", alteracoes.get(0).getCampo());
        assertEquals("Rua das Flores", alteracoes.get(0).getValorAnterior());
        assertEquals("Av. Brasil", alteracoes.get(0).getValorNovo());
    }

    @Test
    void alteracaoDeTelefoneEhAuditadaComMascara() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("Rua das Flores", "(11) 98765-4321")));

        service.salvar(cliente("Rua das Flores", "(21) 91234-0000"));

        AlteracaoCampo telefone = capturar().get(0);
        assertEquals("telefone", telefone.getCampo());
        assertEquals("(11) *****-4321", telefone.getValorAnterior());
        assertEquals("(21) *****-0000", telefone.getValorNovo());
        assertFalse(telefone.getValorNovo().contains("91234"));
    }

    @Test
    void semAlteracoesNaoGeraAuditoria() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("Rua das Flores", "(11) 98765-4321")));
        service.salvar(cliente("Rua das Flores", "(11) 98765-4321"));
        verify(auditoriaService, never()).registrar(any(), anyString(), anyString(), anyList(), any());
    }

    @Test
    void inclusaoDeClienteEhAuditada() {
        Cliente novo = cliente("Rua das Flores", "(11) 98765-4321");
        novo.setId(null);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            c.setId("novo");
            return c;
        });

        service.novoCliente(novo);

        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_CLIENTE), eq("CLIENTE"), eq("novo"), anyList(), isNull());
    }

    @Test
    void telefoneInvalidoEhRejeitado() {
        assertThrows(RegraNegocioException.class, () -> service.salvar(cliente("Rua A", "123")));
        verify(clienteRepository, never()).save(any());
    }

    @SuppressWarnings("unchecked")
    private List<AlteracaoCampo> capturar() {
        ArgumentCaptor<List<AlteracaoCampo>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_CLIENTE), eq("CLIENTE"), eq("c1"), captor.capture(), isNull());
        assertTrue(captor.getValue().stream().noneMatch(a -> a.getCampo().equals("nome")));
        return captor.getValue();
    }

    private static Cliente cliente(String logradouro, String telefone) {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Maria Souza");
        c.setTelefone(telefone);
        c.setLogradouro(logradouro);
        c.setNumero("100");
        c.setCidade("São Paulo");
        c.setEstado("SP");
        return c;
    }
}

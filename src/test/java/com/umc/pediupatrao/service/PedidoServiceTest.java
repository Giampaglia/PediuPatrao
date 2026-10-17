package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private UsuarioLogado usuarioLogado;

    private PedidoService service;

    @BeforeEach
    void setUp() {
        service = new PedidoService(pedidoRepository, produtoRepository, clienteRepository, auditoriaService, usuarioLogado);
        lenient().when(usuarioLogado.username()).thenReturn("atendente01");
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("665f1c2ab4d3e9a1b2c3d4e5");
            }
            return p;
        });
    }

    // ===== RF02 — registro e cálculo no servidor =====

    @Test
    void criarPedidoCalculaTotalNoServidorERegistraEntrada() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente()));
        when(produtoRepository.findById("calabresa")).thenReturn(Optional.of(produto("calabresa", "Pizza Calabresa", 49.90, true)));
        when(produtoRepository.findById("refri")).thenReturn(Optional.of(produto("refri", "Refrigerante 2L", 12.00, true)));

        Pedido pedido = service.criarPedido(requisicao(TipoEntrega.ENTREGA,
                new ItemPedidoRequest("calabresa", 2), new ItemPedidoRequest("refri", 1)));

        assertEquals(new BigDecimal("111.80"), pedido.getSubtotal());
        assertEquals(new BigDecimal("111.80"), pedido.getValorTotal());
        assertEquals(StatusPedido.RECEBIDO, pedido.getStatus());
        assertEquals("atendente01", pedido.getResponsavelEntrada());
        assertNotNull(pedido.getDataHoraEntrada());
        assertEquals(1, pedido.getHistorico().size());
        assertEquals("Maria Souza", pedido.getClienteNome());
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_PEDIDO), eq("PEDIDO"), eq(pedido.getId()), anyList(), isNull());
    }

    @Test
    void criarPedidoSemItensEhRejeitado() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente()));
        assertThrows(RegraNegocioException.class, () -> service.criarPedido(requisicao(TipoEntrega.RETIRADA)));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void criarPedidoComQuantidadeInvalidaEhRejeitado() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente()));
        when(produtoRepository.findById("calabresa")).thenReturn(Optional.of(produto("calabresa", "Pizza Calabresa", 49.90, true)));
        assertThrows(RegraNegocioException.class,
                () -> service.criarPedido(requisicao(TipoEntrega.RETIRADA, new ItemPedidoRequest("calabresa", 0))));
    }

    @Test
    void criarPedidoComProdutoInativoEhRejeitado() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente()));
        when(produtoRepository.findById("velha")).thenReturn(Optional.of(produto("velha", "Pizza Antiga", 30.0, false)));
        assertThrows(RegraNegocioException.class,
                () -> service.criarPedido(requisicao(TipoEntrega.RETIRADA, new ItemPedidoRequest("velha", 1))));
    }

    @Test
    void pedidoParaEntregaExigeEnderecoDoCliente() {
        Cliente semEndereco = cliente();
        semEndereco.setLogradouro(null);
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(semEndereco));
        assertThrows(RegraNegocioException.class,
                () -> service.criarPedido(requisicao(TipoEntrega.ENTREGA, new ItemPedidoRequest("x", 1))));
    }

    // ===== RF03 — transições, entrada e saída =====

    @Test
    void fluxoCompletoDeEntregaRegistraSaidaEResponsavel() {
        Pedido pedido = pedidoExistente(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);

        service.alterarStatus("p1", StatusPedido.EM_PREPARACAO);
        service.alterarStatus("p1", StatusPedido.PRONTO);
        assertNull(pedido.getDataHoraSaida());

        when(usuarioLogado.username()).thenReturn("gerente01");
        service.alterarStatus("p1", StatusPedido.SAIU_PARA_ENTREGA);
        assertNotNull(pedido.getDataHoraSaida());
        assertEquals("gerente01", pedido.getResponsavelSaida());

        service.alterarStatus("p1", StatusPedido.FINALIZADO);
        assertEquals(StatusPedido.FINALIZADO, pedido.getStatus());
        assertNotNull(pedido.getDataHoraFinalizacao());
        assertEquals(4, pedido.getHistorico().size());
    }

    @Test
    void naoPermiteFinalizarPedidoEmPreparacao() {
        pedidoExistente(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA);
        assertThrows(RegraNegocioException.class, () -> service.alterarStatus("p1", StatusPedido.FINALIZADO));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void naoPermiteRetiradoEmPedidoDeEntrega() {
        pedidoExistente(StatusPedido.PRONTO, TipoEntrega.ENTREGA);
        assertThrows(RegraNegocioException.class, () -> service.alterarStatus("p1", StatusPedido.RETIRADO));
    }

    @Test
    void cancelamentoNaoPodeSerFeitoPelaTransicaoComum() {
        pedidoExistente(StatusPedido.RECEBIDO, TipoEntrega.RETIRADA);
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", "CANCELADO"));
    }

    @Test
    void pedidoCanceladoNaoAceitaNovasAlteracoes() {
        pedidoExistente(StatusPedido.CANCELADO, TipoEntrega.RETIRADA);
        assertThrows(RegraNegocioException.class, () -> service.alterarStatus("p1", StatusPedido.EM_PREPARACAO));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", BigDecimal.TEN, null));
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "Justificativa repetida"));
    }

    // ===== RF04 — desconto =====

    @Test
    void descontoAcimaDe20PorCentoEhRejeitado() {
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", new BigDecimal("20.01"), null));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void descontoRecalculaTotalEAuditaValoresAnterioresENovos() {
        Pedido pedido = pedidoExistente(StatusPedido.EM_PREPARACAO, TipoEntrega.RETIRADA); // subtotal 100,00
        when(usuarioLogado.username()).thenReturn("gerente01");

        service.aplicarDesconto("p1", new BigDecimal("10"), "Cliente fidelidade");

        assertEquals(new BigDecimal("10.00"), pedido.getValorDesconto());
        assertEquals(new BigDecimal("90.00"), pedido.getValorTotal());
        assertEquals("gerente01", pedido.getDescontoAplicadoPor());

        List<AlteracaoCampo> alteracoes = capturarAuditoria(TipoOperacao.APLICACAO_DESCONTO, "Cliente fidelidade");
        AlteracaoCampo total = campo(alteracoes, "valorTotal");
        assertEquals("100", total.getValorAnterior());
        assertEquals("90", total.getValorNovo());
    }

    @Test
    void descontoAposSaidaEhRejeitado() {
        pedidoExistente(StatusPedido.SAIU_PARA_ENTREGA, TipoEntrega.ENTREGA);
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", BigDecimal.TEN, null));
    }

    // ===== RF04 — cancelamento =====

    @Test
    void cancelamentoExigeJustificativa() {
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "  "));
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "curta"));
    }

    @Test
    void cancelamentoAposSaidaEhRejeitado() {
        pedidoExistente(StatusPedido.RETIRADO, TipoEntrega.RETIRADA);
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "Cliente desistiu do pedido"));
    }

    @Test
    void cancelamentoPreservaDadosERegistraAuditoria() {
        Pedido pedido = pedidoExistente(StatusPedido.PRONTO, TipoEntrega.RETIRADA);
        when(usuarioLogado.username()).thenReturn("gerente01");

        service.cancelar("p1", "Cliente desistiu do pedido");

        assertEquals(StatusPedido.CANCELADO, pedido.getStatus());
        assertEquals("gerente01", pedido.getResponsavelCancelamento());
        assertEquals("Cliente desistiu do pedido", pedido.getJustificativaCancelamento());
        assertEquals(1, pedido.getItens().size());
        assertEquals(new BigDecimal("100.00"), pedido.getValorTotal());

        List<AlteracaoCampo> alteracoes = capturarAuditoria(TipoOperacao.CANCELAMENTO_PEDIDO, "Cliente desistiu do pedido");
        assertEquals("PRONTO", campo(alteracoes, "status").getValorAnterior());
        assertEquals("CANCELADO", campo(alteracoes, "status").getValorNovo());
    }

    // ===== auxiliares =====

    private Pedido pedidoExistente(StatusPedido status, TipoEntrega tipo) {
        Pedido pedido = new Pedido();
        pedido.setId("p1");
        pedido.setTipoEntrega(tipo);
        pedido.setItens(new ArrayList<>(List.of(ItemPedido.de(produto("pz", "Pizza", 50.0, true), 2))));
        pedido.recalcularValores();
        pedido.setStatus(status);
        lenient().when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedido));
        return pedido;
    }

    @SuppressWarnings("unchecked")
    private List<AlteracaoCampo> capturarAuditoria(TipoOperacao operacao, String justificativa) {
        ArgumentCaptor<List<AlteracaoCampo>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoriaService).registrar(eq(operacao), eq("PEDIDO"), eq("p1"), captor.capture(), eq(justificativa));
        return captor.getValue();
    }

    private static AlteracaoCampo campo(List<AlteracaoCampo> alteracoes, String nome) {
        return alteracoes.stream().filter(a -> a.getCampo().equals(nome)).findFirst()
                .orElseThrow(() -> new AssertionError("Campo não auditado: " + nome));
    }

    private static NovoPedidoRequest requisicao(TipoEntrega tipo, ItemPedidoRequest... itens) {
        NovoPedidoRequest req = new NovoPedidoRequest();
        req.setClienteId("c1");
        req.setTipoEntrega(tipo);
        req.setItens(new ArrayList<>(List.of(itens)));
        return req;
    }

    private static Cliente cliente() {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Maria Souza");
        c.setTelefone("(11) 98765-4321");
        c.setLogradouro("Rua das Flores");
        return c;
    }

    private static Produto produto(String id, String nome, double preco, boolean ativo) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setPreco(preco);
        p.setAtivo(ativo);
        return p;
    }

    @Test
    void transicoesDoEnumSeguemOFluxoObrigatorio() {
        assertTrue(StatusPedido.RECEBIDO.podeIrPara(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA));
        assertTrue(StatusPedido.PRONTO.podeIrPara(StatusPedido.RETIRADO, TipoEntrega.RETIRADA));
        assertTrue(StatusPedido.SAIU_PARA_ENTREGA.podeIrPara(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA));
        assertTrue(StatusPedido.CANCELADO.proximos(TipoEntrega.ENTREGA).isEmpty());
        assertTrue(StatusPedido.FINALIZADO.proximos(TipoEntrega.ENTREGA).isEmpty());
    }
}

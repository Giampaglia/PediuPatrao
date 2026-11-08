package com.umc.pediupatrao.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.service.PedidoService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Os pedidos de demonstração precisam respeitar as mesmas regras dos pedidos reais. */
class DadosDemonstracaoConfigTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 8, 20, 0);

    @Test
    void pedidosGeradosRespeitamPeriodoFluxoEValores() {
        List<Pedido> pedidos = DadosDemonstracaoConfig.gerar(produtos(), clientes(), AGORA, 30, new Random(1));

        assertFalse(pedidos.isEmpty());
        for (Pedido p : pedidos) {
            assertNotNull(p.getStatus());
            assertFalse(p.getDataHoraEntrada().isAfter(AGORA), "entrada no futuro");
            assertFalse(p.getDataHoraEntrada().isBefore(AGORA.toLocalDate().minusDays(29).atStartOfDay()), "fora do período");
            assertFalse(p.getItens().isEmpty());
            assertTrue(p.getPercentualDesconto().compareTo(PedidoService.DESCONTO_MAXIMO) <= 0, "desconto acima de 20%");
            assertEquals(p.getSubtotal().subtract(p.getValorDesconto()), p.getValorTotal());
            // histórico começa em RECEBIDO e cada passo é uma transição permitida
            List<HistoricoStatus> h = p.getHistorico();
            assertEquals(StatusPedido.RECEBIDO, h.get(0).getStatus());
            for (int i = 1; i < h.size(); i++) {
                StatusPedido de = h.get(i - 1).getStatus();
                StatusPedido para = h.get(i).getStatus();
                assertTrue(para == StatusPedido.CANCELADO ? de.isAntesDaSaida() : de.podeIrPara(para, p.getTipoEntrega()),
                        "transição inválida " + de + " -> " + para);
            }
            assertEquals(h.get(h.size() - 1).getStatus(), p.getStatus());
            if (p.getStatus() == StatusPedido.FINALIZADO) {
                assertNotNull(p.getDataHoraSaida());
            }
        }
        assertTrue(pedidos.stream().anyMatch(p -> p.getStatus() == StatusPedido.CANCELADO));
        assertTrue(pedidos.stream().anyMatch(p -> p.getPercentualDesconto().signum() > 0));
    }

    private static List<Produto> produtos() {
        Produto pizza = new Produto();
        pizza.setId("pz");
        pizza.setNome("Pizza Calabresa");
        pizza.setTipo("Pizza");
        pizza.setPreco(49.90);
        pizza.setAtivo(true);
        Produto esfiha = new Produto();
        esfiha.setId("es");
        esfiha.setNome("Esfiha de Carne");
        esfiha.setTipo("Esfiha");
        esfiha.setPreco(7.50);
        esfiha.setAtivo(true);
        return List.of(pizza, esfiha);
    }

    private static List<Cliente> clientes() {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Ana Lima");
        return List.of(c);
    }
}

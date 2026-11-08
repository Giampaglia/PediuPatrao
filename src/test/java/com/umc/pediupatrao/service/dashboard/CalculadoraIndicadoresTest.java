package com.umc.pediupatrao.service.dashboard;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.PedidoRecente;
import com.umc.pediupatrao.dto.dashboard.RankingProdutos;
import com.umc.pediupatrao.dto.dashboard.ResumoVendas;
import com.umc.pediupatrao.dto.dashboard.SerieVendas;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidos;
import com.umc.pediupatrao.dto.dashboard.VendasPorCategoria;
import com.umc.pediupatrao.dto.dashboard.VendasPorTipoEntrega;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Regras de cálculo dos indicadores (RF01–RF04, RF07), sem banco de dados. */
class CalculadoraIndicadoresTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 2);   // segunda-feira
    private static final LocalDate FIM = LocalDate.of(2026, 11, 8);      // domingo
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 8, 20, 0);

    private Produto pizza;
    private Produto refri;
    private CalculadoraIndicadores calc;
    private FiltroDashboard filtro;

    @BeforeEach
    void setUp() {
        pizza = produto("pizza", "Pizza Calabresa", "Pizza", 50.00);
        refri = produto("refri", "Refrigerante 2L", "Bebida", 10.00);
        calc = new CalculadoraIndicadores(List.of(pizza, refri), AGORA);
        filtro = new FiltroDashboard(INICIO, FIM, Set.of(), null, null, null);
    }

    // ===== RF01 — indicadores gerais =====

    @Test
    void faturamentoETicketMedioIgnoramPedidosCancelados() {
        List<Pedido> pedidos = List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(3, 19), item(pizza, 2)),              // 100
                pedido(StatusPedido.PRONTO, TipoEntrega.RETIRADA, dia(4, 20), item(pizza, 1), item(refri, 1)), // 60
                pedido(StatusPedido.CANCELADO, TipoEntrega.ENTREGA, dia(5, 21), item(pizza, 1)));              // 50

        ResumoVendas r = calc.resumo(filtro, pedidos, List.of(), 0);

        assertEquals(new BigDecimal("160.00"), r.faturamento());
        assertEquals(3, r.totalPedidos());
        assertEquals(2, r.pedidosValidos());
        assertEquals(1, r.pedidosCancelados());
        assertEquals(new BigDecimal("33.3"), r.taxaCancelamento());
        assertEquals(new BigDecimal("80.00"), r.ticketMedio());
        assertEquals(4, r.itensVendidos());
        assertEquals(1, r.pedidosEmAndamento());
        assertEquals(INICIO, r.periodo().dataInicio());
        assertEquals(7, r.periodo().dias());
    }

    @Test
    void descontoReduzFaturamentoEEhSomadoNosDescontos() {
        Pedido comDesconto = pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(3, 19), item(pizza, 2));
        aplicarDesconto(comDesconto, "10");

        ResumoVendas r = calc.resumo(filtro, List.of(comDesconto), List.of(), 0);

        assertEquals(new BigDecimal("90.00"), r.faturamento());
        assertEquals(new BigDecimal("10.00"), r.totalDescontos());
        assertEquals(1, r.pedidosComDesconto());
    }

    @Test
    void comparativoCalculaVariacaoEmRelacaoAoPeriodoAnterior() {
        List<Pedido> atual = List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(3, 19), item(pizza, 2)),
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(4, 19), item(pizza, 1)));
        List<Pedido> anterior = List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, LocalDateTime.of(2026, 10, 28, 19, 0), item(pizza, 1)));

        ResumoVendas.Comparativo c = calc.resumo(filtro, atual, anterior, 0).comparativo();

        assertEquals(LocalDate.of(2026, 10, 26), c.periodoAnterior().dataInicio());
        assertEquals(LocalDate.of(2026, 11, 1), c.periodoAnterior().dataFim());
        assertEquals(new BigDecimal("50.00"), c.faturamento());
        assertEquals(new BigDecimal("200.0"), c.variacaoFaturamento());   // 150 vs 50
        assertEquals(new BigDecimal("100.0"), c.variacaoPedidos());       // 2 vs 1
        assertEquals(new BigDecimal("50.0"), c.variacaoTicketMedio());    // 75 vs 50
    }

    @Test
    void semVendasNoPeriodoAnteriorAVariacaoFicaNula() {
        List<Pedido> atual = List.of(pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(3, 19), item(pizza, 1)));

        ResumoVendas.Comparativo c = calc.resumo(filtro, atual, List.of(), 0).comparativo();

        assertNull(c.variacaoFaturamento());
        assertNull(c.variacaoPedidos());
    }

    @Test
    void periodoSemPedidosRetornaZerosSemErro() {
        ResumoVendas r = calc.resumo(filtro, List.of(), List.of(), 0);

        assertEquals(new BigDecimal("0.00"), r.faturamento());
        assertEquals(new BigDecimal("0.00"), r.ticketMedio());
        assertEquals(new BigDecimal("0.0"), r.taxaCancelamento());
        assertNull(r.tempoMedioAteSaidaMinutos());
    }

    @Test
    void tempoMedioAteSaidaUsaEntradaESaida() {
        Pedido p1 = pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(3, 19), item(pizza, 1));
        p1.setDataHoraSaida(p1.getDataHoraEntrada().plusMinutes(30));
        Pedido p2 = pedido(StatusPedido.FINALIZADO, TipoEntrega.RETIRADA, dia(4, 19), item(pizza, 1));
        p2.setDataHoraSaida(p2.getDataHoraEntrada().plusMinutes(50));
        Pedido semSaida = pedido(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA, dia(5, 19), item(pizza, 1));

        ResumoVendas r = calc.resumo(filtro, List.of(p1, p2, semSaida), List.of(), 0);

        assertEquals(new BigDecimal("40.0"), r.tempoMedioAteSaidaMinutos());
    }

    // ===== RF07 — tratamento de dados incompletos =====

    @Test
    void pedidosAntigosComCamposNulosNaoQuebramOsCalculos() {
        Pedido semStatus = new Pedido();       // status desconhecido gravado pela versão anterior
        semStatus.setDataHoraEntrada(dia(3, 12));
        semStatus.setValorTotal(new BigDecimal("30.00"));
        Pedido semValores = new Pedido();      // sem itens, subtotal e total
        semValores.setStatus(StatusPedido.FINALIZADO);
        semValores.setDataHoraEntrada(dia(4, 12));
        semValores.setItens(null);
        semValores.setSubtotal(null);
        semValores.setValorTotal(null);
        semValores.setValorDesconto(null);
        List<Pedido> pedidos = Arrays.asList(semStatus, semValores, null);

        ResumoVendas r = assertDoesNotThrow(() -> calc.resumo(filtro, pedidos, null, 4));

        assertEquals(2, r.totalPedidos());
        assertEquals(1, r.pedidosValidos());
        assertEquals(new BigDecimal("0.00"), r.faturamento()); // pedido sem status não conta como venda
        assertEquals(1, r.qualidadeDados().pedidosSemStatus());
        assertEquals(1, r.qualidadeDados().pedidosSemValor());
        assertEquals(4, r.qualidadeDados().pedidosSemDataEntrada());
        assertDoesNotThrow(() -> calc.serie(filtro, pedidos, Agrupamento.DIA));
        assertDoesNotThrow(() -> calc.ranking(filtro, pedidos, CriterioRanking.QUANTIDADE, 10));
        assertDoesNotThrow(() -> calc.situacao(filtro, pedidos));
        assertDoesNotThrow(() -> calc.recentes(pedidos));
    }

    // ===== RF02 — séries =====

    @Test
    void seriePorDiaIncluiDiasSemVendaComZero() {
        List<Pedido> pedidos = List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(2, 19), item(pizza, 1)),
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(2, 20), item(refri, 1)),
                pedido(StatusPedido.CANCELADO, TipoEntrega.ENTREGA, dia(3, 20), item(pizza, 4)),
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(8, 19), item(pizza, 2)));

        SerieVendas s = calc.serie(filtro, pedidos, Agrupamento.DIA);

        assertEquals(7, s.pontos().size());
        assertEquals("2026-11-02", s.pontos().get(0).chave());
        assertEquals("02/11", s.pontos().get(0).rotulo());
        assertEquals(2, s.pontos().get(0).pedidos());
        assertEquals(new BigDecimal("60.00"), s.pontos().get(0).faturamento());
        assertEquals(new BigDecimal("30.00"), s.pontos().get(0).ticketMedio());
        assertEquals(0, s.pontos().get(1).pedidos()); // dia 03: só um cancelado
        assertEquals(new BigDecimal("100.00"), s.pontos().get(6).faturamento());
        assertEquals(3, s.totalPedidos());
        assertEquals(new BigDecimal("160.00"), s.faturamentoTotal());
    }

    @Test
    void seriePorSemanaComecaNaSegundaEPorMesUsaRotuloAbreviado() {
        FiltroDashboard doisMeses = new FiltroDashboard(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 8), Set.of(), null, null, null);
        List<Pedido> pedidos = List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, LocalDateTime.of(2026, 11, 1, 19, 0), item(pizza, 1)), // domingo
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, LocalDateTime.of(2026, 10, 26, 19, 0), item(pizza, 1))); // segunda

        SerieVendas semanas = calc.serie(doisMeses, pedidos, Agrupamento.SEMANA);
        SerieVendas meses = calc.serie(doisMeses, pedidos, Agrupamento.MES);

        assertEquals("2026-09-28", semanas.pontos().get(0).chave()); // segunda da semana de 01/10
        SerieVendas.Ponto semana26 = semanas.pontos().stream().filter(p -> p.chave().equals("2026-10-26")).findFirst().orElseThrow();
        assertEquals(2, semana26.pedidos());
        assertEquals("Sem. 26/10", semana26.rotulo());
        assertEquals(2, meses.pontos().size());
        assertEquals("2026-10", meses.pontos().get(0).chave());
        assertEquals("out/26", meses.pontos().get(0).rotulo());
        assertEquals("nov/26", meses.pontos().get(1).rotulo());
        assertEquals(1, meses.pontos().get(1).pedidos());
    }

    @Test
    void distribuicaoPorHoraEDiaDaSemanaTemTodosOsPontos() {
        List<Pedido> pedidos = List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(2, 19), item(pizza, 1)),  // segunda 19h
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(8, 19), item(pizza, 1)),  // domingo 19h
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(8, 12), item(pizza, 1))); // domingo 12h

        SerieVendas horas = calc.serie(filtro, pedidos, Agrupamento.HORA_DO_DIA);
        SerieVendas dias = calc.serie(filtro, pedidos, Agrupamento.DIA_DA_SEMANA);

        assertEquals(24, horas.pontos().size());
        assertEquals("19h", horas.pontos().get(19).rotulo());
        assertEquals(2, horas.pontos().get(19).pedidos());
        assertEquals(7, dias.pontos().size());
        assertEquals("SEG", dias.pontos().get(0).chave());
        assertEquals(1, dias.pontos().get(0).pedidos());
        assertEquals("Dom", dias.pontos().get(6).rotulo());
        assertEquals(2, dias.pontos().get(6).pedidos());
    }

    @Test
    void vendasPorCategoriaETipoDeEntrega() {
        Produto excluido = produto("antigo", "Produto excluído", null, 0);
        List<Pedido> pedidos = List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(3, 19), item(pizza, 2), item(refri, 1)), // 110
                pedido(StatusPedido.FINALIZADO, TipoEntrega.RETIRADA, dia(4, 19), item(refri, 3)),               // 30
                pedido(StatusPedido.FINALIZADO, TipoEntrega.RETIRADA, dia(4, 20), itemSemCadastro(excluido, 10.00, 1)),
                pedido(StatusPedido.CANCELADO, TipoEntrega.ENTREGA, dia(5, 19), item(pizza, 5)));

        VendasPorCategoria cat = calc.categorias(filtro, pedidos);
        VendasPorTipoEntrega tipos = calc.tiposEntrega(filtro, pedidos);

        assertEquals("Pizza", cat.categorias().get(0).categoria());
        assertEquals(new BigDecimal("100.00"), cat.categorias().get(0).faturamento());
        assertEquals(new BigDecimal("66.7"), cat.categorias().get(0).participacao());
        assertEquals("Bebida", cat.categorias().get(1).categoria());
        assertEquals(4, cat.categorias().get(1).quantidade());
        assertEquals(CalculadoraIndicadores.SEM_CATEGORIA, cat.categorias().get(2).categoria());
        assertEquals(new BigDecimal("150.00"), cat.faturamentoTotal());

        assertEquals(3, tipos.totalPedidos());
        assertEquals(TipoEntrega.ENTREGA, tipos.tipos().get(0).tipo());
        assertEquals(1, tipos.tipos().get(0).pedidos());
        assertEquals(new BigDecimal("33.3"), tipos.tipos().get(0).participacao());
        assertEquals(new BigDecimal("20.00"), tipos.tipos().get(1).ticketMedio()); // (30 + 10) / 2
    }

    // ===== RF03 — situação dos pedidos =====

    @Test
    void situacaoListaTodosOsStatusInclusiveSemPedidos() {
        List<Pedido> pedidos = List.of(
                pedido(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA, dia(8, 19), item(pizza, 1)),
                pedido(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA, dia(8, 19), item(pizza, 1)),
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(7, 19), item(pizza, 1)),
                pedido(StatusPedido.CANCELADO, TipoEntrega.ENTREGA, dia(7, 19), item(pizza, 1)));

        SituacaoPedidos s = calc.situacao(filtro, pedidos);

        assertEquals(StatusPedido.values().length, s.porStatus().size());
        assertEquals(4, s.total());
        assertEquals(2, s.emAndamento());
        assertEquals(1, s.finalizados());
        assertEquals(1, s.cancelados());
        assertEquals(new BigDecimal("25.0"), s.porStatus().get(0).percentual());
        assertEquals(0, s.porStatus().get(2).quantidade()); // PRONTO
    }

    @Test
    void pedidosRecentesInformamMinutosDesdeAEntrada() {
        Pedido p = pedido(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA, AGORA.minusMinutes(25), item(pizza, 2));
        p.setId("665f1c2ab4d3e9a1b2c3d4e5");

        PedidoRecente r = calc.recentes(List.of(p)).get(0);

        assertEquals("C3D4E5", r.codigo());
        assertEquals(25L, r.minutosDesdeEntrada());
        assertEquals("Em preparação", r.statusDescricao());
        assertEquals(2, r.quantidadeItens());
    }

    // ===== RF04 — ranking =====

    @Test
    void rankingOrdenaPorQuantidadeOuFaturamentoESemCancelados() {
        List<Pedido> pedidos = List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(3, 19), item(pizza, 2), item(refri, 2)),
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(4, 19), item(refri, 3)),
                pedido(StatusPedido.CANCELADO, TipoEntrega.ENTREGA, dia(5, 19), item(pizza, 10)));

        RankingProdutos porQuantidade = calc.ranking(filtro, pedidos, CriterioRanking.QUANTIDADE, 10);
        RankingProdutos porFaturamento = calc.ranking(filtro, pedidos, CriterioRanking.FATURAMENTO, 10);
        RankingProdutos top1 = calc.ranking(filtro, pedidos, CriterioRanking.QUANTIDADE, 1);

        RankingProdutos.ProdutoVendido primeiro = porQuantidade.produtos().get(0);
        assertEquals("refri", primeiro.produtoId());
        assertEquals(1, primeiro.posicao());
        assertEquals(5, primeiro.quantidade());
        assertEquals(2, primeiro.pedidos());
        assertEquals("Bebida", primeiro.categoria());
        assertEquals(new BigDecimal("71.4"), primeiro.participacao()); // 5 de 7 itens
        assertEquals(7, porQuantidade.totalItensVendidos());

        assertEquals("pizza", porFaturamento.produtos().get(0).produtoId());
        assertEquals(new BigDecimal("100.00"), porFaturamento.produtos().get(0).faturamento());
        assertEquals(new BigDecimal("66.7"), porFaturamento.produtos().get(0).participacao()); // 100 de 150

        assertEquals(1, top1.produtos().size());
        assertEquals(7, top1.totalItensVendidos()); // total continua considerando todos os produtos
    }

    // ===== RF05 — filtro de categoria/produto em nível de item =====

    @Test
    void filtroDeCategoriaConsideraSoOsItensDaCategoriaComDescontoRateado() {
        Pedido misto = pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, dia(3, 19), item(pizza, 1), item(refri, 1)); // 60
        aplicarDesconto(misto, "10"); // total 54
        FiltroDashboard bebidas = new FiltroDashboard(INICIO, FIM, Set.of(), null, "bebida", null);

        ResumoVendas r = calc.resumo(bebidas, List.of(misto), List.of(), 0);
        RankingProdutos ranking = calc.ranking(bebidas, List.of(misto), CriterioRanking.QUANTIDADE, 10);

        assertEquals(new BigDecimal("9.00"), r.faturamento());     // 10,00 com 10% de desconto
        assertEquals(new BigDecimal("1.00"), r.totalDescontos());
        assertEquals(1, r.itensVendidos());
        assertEquals(1, ranking.produtos().size());
        assertEquals("refri", ranking.produtos().get(0).produtoId());
    }

    // ===== apoio =====

    private static LocalDateTime dia(int diaDoMes, int hora) {
        return LocalDateTime.of(2026, 11, diaDoMes, hora, 0);
    }

    private static Produto produto(String id, String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        return p;
    }

    private static ItemPedido item(Produto produto, int quantidade) {
        return ItemPedido.de(produto, quantidade);
    }

    private static ItemPedido itemSemCadastro(Produto produto, double preco, int quantidade) {
        produto.setPreco(preco);
        return ItemPedido.de(produto, quantidade);
    }

    private static Pedido pedido(StatusPedido status, TipoEntrega tipo, LocalDateTime entrada, ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setStatus(status);
        p.setTipoEntrega(tipo);
        p.setDataHoraEntrada(entrada);
        p.setItens(new ArrayList<>(List.of(itens)));
        p.recalcularValores();
        return p;
    }

    private static void aplicarDesconto(Pedido pedido, String percentual) {
        pedido.setPercentualDesconto(new BigDecimal(percentual));
        pedido.recalcularValores();
    }
}

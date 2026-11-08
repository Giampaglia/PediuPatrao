package com.umc.pediupatrao.service.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltro;
import com.umc.pediupatrao.dto.dashboard.ParametrosDashboard;
import com.umc.pediupatrao.dto.dashboard.ResumoVendas;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ConsultaPedidosRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** RF05 — conversão e validação dos filtros e orquestração das consultas. */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 8);

    @Mock
    private ConsultaPedidosRepository consulta;
    @Mock
    private ProdutoRepository produtos;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(ZonedDateTime.of(HOJE.atTime(20, 0), ZONA).toInstant(), ZONA);
        service = new DashboardService(consulta, produtos, clock);
        lenient().when(produtos.findAll()).thenReturn(List.of(
                produto("p1", "Pizza Calabresa", "Pizza"),
                produto("p2", "Pizza Mussarela", " pizza "),
                produto("b1", "Refrigerante 2L", "Bebida")));
        lenient().when(consulta.buscar(any(), any())).thenReturn(List.of());
    }

    @Test
    void semParametrosUsaOsUltimos30DiasAteHoje() {
        FiltroDashboard f = service.filtro(ParametrosDashboard.vazios());

        assertEquals(HOJE.minusDays(29), f.dataInicio());
        assertEquals(HOJE, f.dataFim());
        assertEquals(30, f.dias());
        assertTrue(f.status().isEmpty());
        assertNull(f.tipoEntrega());
    }

    @Test
    void converteStatusRepetidosOuSeparadosPorVirgulaSemDiferenciarMaiusculas() {
        FiltroDashboard f = service.filtro(new ParametrosDashboard("2026-11-01", "2026-11-08",
                List.of("finalizado,CANCELADO", " pronto "), "entrega", "  Pizza ", null));

        assertEquals(Set.of(StatusPedido.FINALIZADO, StatusPedido.CANCELADO, StatusPedido.PRONTO), f.status());
        assertEquals(TipoEntrega.ENTREGA, f.tipoEntrega());
        assertEquals("Pizza", f.categoria());
        assertEquals(8, f.dias());
    }

    @Test
    void dataInicialPosteriorAFinalEhRejeitada() {
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> service.filtro(
                new ParametrosDashboard("2026-11-10", "2026-11-01", null, null, null, null)));
        assertTrue(e.getMessage().contains("não pode ser posterior"));
    }

    @Test
    void dataEmFormatoInvalidoEhRejeitadaComOrientacao() {
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> service.filtro(
                new ParametrosDashboard("08/11/2026", null, null, null, null, null)));
        assertTrue(e.getMessage().contains("dataInicio"));
        assertTrue(e.getMessage().contains("AAAA-MM-DD"));
    }

    @Test
    void periodoAcimaDoLimiteEhRejeitado() {
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> service.filtro(
                new ParametrosDashboard("2025-01-01", "2026-11-08", null, null, null, null)));
        assertTrue(e.getMessage().contains("366"));
    }

    @Test
    void statusOuTipoDeEntregaInexistentesListamOsValoresAceitos() {
        RegraNegocioException status = assertThrows(RegraNegocioException.class, () -> service.filtro(
                new ParametrosDashboard(null, null, List.of("ENTREGUE_ONTEM"), null, null, null)));
        assertTrue(status.getMessage().contains("FINALIZADO"));

        RegraNegocioException tipo = assertThrows(RegraNegocioException.class, () -> service.filtro(
                new ParametrosDashboard(null, null, null, "DRONE", null, null)));
        assertTrue(tipo.getMessage().contains("ENTREGA, RETIRADA"));
    }

    @Test
    void limiteEAgrupamentoInvalidosSaoRejeitados() {
        assertThrows(RegraNegocioException.class, () -> service.recentes(ParametrosDashboard.vazios(), 0));
        assertThrows(RegraNegocioException.class, () -> service.maisVendidos(ParametrosDashboard.vazios(), null, 51));
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> service.vendas(ParametrosDashboard.vazios(), "ANO"));
        assertTrue(e.getMessage().contains("HORA_DO_DIA"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtroDeCategoriaViraListaDeProdutosParaOBanco() {
        service.categorias(new ParametrosDashboard(null, null, null, null, "PIZZA", null));

        ArgumentCaptor<Collection<String>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(consulta).buscar(any(), ids.capture());
        assertEquals(Set.of("p1", "p2"), Set.copyOf(ids.getValue()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void categoriaSemProdutosResultaEmListaVazia() {
        service.categorias(new ParametrosDashboard(null, null, null, null, "Sobremesa", null));

        ArgumentCaptor<Collection<String>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(consulta).buscar(any(), ids.capture());
        assertTrue(ids.getValue().isEmpty());
    }

    @Test
    void semFiltroDeProdutoNaoRestringeItens() {
        service.situacao(ParametrosDashboard.vazios());
        verify(consulta).buscar(any(), isNull());
    }

    @Test
    void resumoConsultaTambemOPeriodoAnterior() {
        when(consulta.contarSemDataEntrada()).thenReturn(2L);

        ResumoVendas r = service.resumo(new ParametrosDashboard("2026-11-01", "2026-11-07", null, null, null, null));

        ArgumentCaptor<FiltroDashboard> filtros = ArgumentCaptor.forClass(FiltroDashboard.class);
        verify(consulta, org.mockito.Mockito.times(2)).buscar(filtros.capture(), isNull());
        assertEquals(LocalDate.of(2026, 11, 1), filtros.getAllValues().get(0).dataInicio());
        assertEquals(LocalDate.of(2026, 10, 25), filtros.getAllValues().get(1).dataInicio());
        assertEquals(LocalDate.of(2026, 10, 31), filtros.getAllValues().get(1).dataFim());
        assertEquals(2, r.qualidadeDados().pedidosSemDataEntrada());
    }

    @Test
    void recentesUsaLimitePadrao() {
        when(consulta.buscarRecentes(any(), any(), anyInt())).thenReturn(List.of());
        service.recentes(ParametrosDashboard.vazios(), null);
        verify(consulta).buscarRecentes(any(), isNull(), eq(DashboardService.LIMITE_PADRAO));
    }

    @Test
    void opcoesDeFiltroUnificamCategoriasEInformamPeriodoPadrao() {
        when(consulta.primeiraEntrada()).thenReturn(Optional.empty());
        when(consulta.ultimaEntrada()).thenReturn(Optional.empty());

        OpcoesFiltro o = service.opcoesFiltro();

        assertEquals(List.of("Bebida", "Pizza"), o.categorias());
        assertEquals(StatusPedido.values().length, o.status().size());
        assertEquals(3, o.produtos().size());
        assertEquals(HOJE, o.periodoPadrao().dataFim());
        assertEquals(DashboardService.LIMITE_PERIODO_DIAS, o.limitePeriodoDias());
    }

    private static Produto produto(String id, String nome, String tipo) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(10.0);
        p.setAtivo(true);
        return p;
    }
}

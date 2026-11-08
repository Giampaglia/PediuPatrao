package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.dashboard.OpcoesFiltro;
import com.umc.pediupatrao.dto.dashboard.ParametrosDashboard;
import com.umc.pediupatrao.dto.dashboard.PedidoRecente;
import com.umc.pediupatrao.dto.dashboard.RankingProdutos;
import com.umc.pediupatrao.dto.dashboard.ResumoVendas;
import com.umc.pediupatrao.dto.dashboard.SerieVendas;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidos;
import com.umc.pediupatrao.dto.dashboard.VendasPorCategoria;
import com.umc.pediupatrao.dto.dashboard.VendasPorTipoEntrega;
import com.umc.pediupatrao.service.dashboard.DashboardService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de leitura do dashboard administrativo (somente GET, somente ADMIN e GERENTE).
 *
 * Todos os endpoints aceitam os filtros comuns de {@link ParametrosDashboard}
 * (dataInicio, dataFim, status, tipoEntrega, categoria, produtoId).
 * Documentação completa: docs/API.md.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** RF01 — indicadores gerais com comparação ao período anterior. */
    @GetMapping("/resumo")
    public ResumoVendas resumo(ParametrosDashboard filtros) {
        return dashboardService.resumo(filtros);
    }

    /** RF02 — série de vendas (DIA, SEMANA, MES, HORA_DO_DIA ou DIA_DA_SEMANA). */
    @GetMapping("/vendas")
    public SerieVendas vendas(ParametrosDashboard filtros,
            @RequestParam(required = false) String agrupamento) {
        return dashboardService.vendas(filtros, agrupamento);
    }

    /** RF02 — vendas por categoria de produto. */
    @GetMapping("/vendas/categorias")
    public VendasPorCategoria categorias(ParametrosDashboard filtros) {
        return dashboardService.categorias(filtros);
    }

    /** RF02 — entrega x retirada. */
    @GetMapping("/vendas/tipos-entrega")
    public VendasPorTipoEntrega tiposEntrega(ParametrosDashboard filtros) {
        return dashboardService.tiposEntrega(filtros);
    }

    /** RF03 — quantidade de pedidos por status. */
    @GetMapping("/pedidos/status")
    public SituacaoPedidos situacao(ParametrosDashboard filtros) {
        return dashboardService.situacao(filtros);
    }

    /** RF03 — últimos pedidos do recorte. */
    @GetMapping("/pedidos/recentes")
    public List<PedidoRecente> recentes(ParametrosDashboard filtros,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.recentes(filtros, limite);
    }

    /** RF04 — ranking de produtos mais vendidos. */
    @GetMapping("/produtos/mais-vendidos")
    public RankingProdutos maisVendidos(ParametrosDashboard filtros,
            @RequestParam(required = false) String ordenarPor,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.maisVendidos(filtros, ordenarPor, limite);
    }

    /** RF05 — opções disponíveis para os filtros. */
    @GetMapping("/filtros")
    public OpcoesFiltro filtros() {
        return dashboardService.opcoesFiltro();
    }
}

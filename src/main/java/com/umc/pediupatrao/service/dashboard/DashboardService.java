package com.umc.pediupatrao.service.dashboard;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltro;
import com.umc.pediupatrao.dto.dashboard.ParametrosDashboard;
import com.umc.pediupatrao.dto.dashboard.PedidoRecente;
import com.umc.pediupatrao.dto.dashboard.Periodo;
import com.umc.pediupatrao.dto.dashboard.RankingProdutos;
import com.umc.pediupatrao.dto.dashboard.ResumoVendas;
import com.umc.pediupatrao.dto.dashboard.SerieVendas;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidos;
import com.umc.pediupatrao.dto.dashboard.VendasPorCategoria;
import com.umc.pediupatrao.dto.dashboard.VendasPorTipoEntrega;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ConsultaPedidosRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Casos de uso do dashboard administrativo (RF01–RF07).
 *
 * Exclusivo de ADMIN e GERENTE: além da regra de URL no SecurityConfig, o
 * {@code @PreAuthorize} da classe protege todos os métodos.
 */
@Service
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
public class DashboardService {

    public static final int DIAS_PADRAO = 30;
    public static final int LIMITE_PERIODO_DIAS = 366;
    public static final int LIMITE_PADRAO = 10;
    public static final int LIMITE_MAXIMO = 50;
    static final int TAMANHO_MAXIMO_TEXTO = 100;

    private final ConsultaPedidosRepository consultaPedidos;
    private final ProdutoRepository produtoRepository;
    private final Clock clock;

    public DashboardService(ConsultaPedidosRepository consultaPedidos, ProdutoRepository produtoRepository, Clock clock) {
        this.consultaPedidos = consultaPedidos;
        this.produtoRepository = produtoRepository;
        this.clock = clock;
    }

    // ========================
    // CONSULTAS
    // ========================

    public ResumoVendas resumo(ParametrosDashboard parametros) {
        Contexto ctx = contexto(parametros);
        FiltroDashboard anterior = ctx.filtro.periodoAnterior();
        return ctx.calculadora.resumo(ctx.filtro,
                consultaPedidos.buscar(ctx.filtro, ctx.produtoIds),
                consultaPedidos.buscar(anterior, ctx.produtoIds),
                consultaPedidos.contarSemDataEntrada());
    }

    public SerieVendas vendas(ParametrosDashboard parametros, String agrupamento) {
        Agrupamento tipo = enumeracao(agrupamento, Agrupamento.class, Agrupamento.DIA, "agrupamento");
        Contexto ctx = contexto(parametros);
        return ctx.calculadora.serie(ctx.filtro, consultaPedidos.buscar(ctx.filtro, ctx.produtoIds), tipo);
    }

    public SituacaoPedidos situacao(ParametrosDashboard parametros) {
        Contexto ctx = contexto(parametros);
        return ctx.calculadora.situacao(ctx.filtro, consultaPedidos.buscar(ctx.filtro, ctx.produtoIds));
    }

    public List<PedidoRecente> recentes(ParametrosDashboard parametros, Integer limite) {
        int quantidade = limite(limite);
        Contexto ctx = contexto(parametros);
        return ctx.calculadora.recentes(consultaPedidos.buscarRecentes(ctx.filtro, ctx.produtoIds, quantidade));
    }

    public RankingProdutos maisVendidos(ParametrosDashboard parametros, String ordenarPor, Integer limite) {
        CriterioRanking criterio = enumeracao(ordenarPor, CriterioRanking.class, CriterioRanking.QUANTIDADE, "ordenarPor");
        int quantidade = limite(limite);
        Contexto ctx = contexto(parametros);
        return ctx.calculadora.ranking(ctx.filtro, consultaPedidos.buscar(ctx.filtro, ctx.produtoIds), criterio, quantidade);
    }

    public VendasPorCategoria categorias(ParametrosDashboard parametros) {
        Contexto ctx = contexto(parametros);
        return ctx.calculadora.categorias(ctx.filtro, consultaPedidos.buscar(ctx.filtro, ctx.produtoIds));
    }

    public VendasPorTipoEntrega tiposEntrega(ParametrosDashboard parametros) {
        Contexto ctx = contexto(parametros);
        return ctx.calculadora.tiposEntrega(ctx.filtro, consultaPedidos.buscar(ctx.filtro, ctx.produtoIds));
    }

    public OpcoesFiltro opcoesFiltro() {
        List<Produto> produtos = produtoRepository.findAll();
        LocalDate hoje = LocalDate.now(clock);
        return new OpcoesFiltro(
                new Periodo(hoje.minusDays(DIAS_PADRAO - 1L), hoje, DIAS_PADRAO),
                LIMITE_PERIODO_DIAS,
                consultaPedidos.primeiraEntrada().orElse(null),
                consultaPedidos.ultimaEntrada().orElse(null),
                Arrays.stream(StatusPedido.values()).map(s -> new OpcoesFiltro.Opcao(s.name(), s.getDescricao())).toList(),
                Arrays.stream(TipoEntrega.values()).map(t -> new OpcoesFiltro.Opcao(t.name(), t.getDescricao())).toList(),
                categorias(produtos),
                produtos.stream()
                        .filter(p -> p.getId() != null && p.getNome() != null)
                        .sorted(Comparator.comparing(p -> p.getNome().toLowerCase(Locale.ROOT)))
                        .map(p -> new OpcoesFiltro.ProdutoOpcao(p.getId(), p.getNome(),
                                vazio(p.getTipo()) ? CalculadoraIndicadores.SEM_CATEGORIA : p.getTipo().trim(),
                                !Boolean.FALSE.equals(p.getAtivo())))
                        .toList(),
                Arrays.stream(Agrupamento.values()).map(a -> new OpcoesFiltro.Opcao(a.name(), a.getDescricao())).toList(),
                Arrays.stream(CriterioRanking.values()).map(c -> new OpcoesFiltro.Opcao(c.name(), c.getDescricao())).toList());
    }

    // ========================
    // FILTROS (RF05) — conversão e validação
    // ========================

    private record Contexto(FiltroDashboard filtro, Set<String> produtoIds, CalculadoraIndicadores calculadora) {
    }

    private Contexto contexto(ParametrosDashboard parametros) {
        FiltroDashboard filtro = filtro(parametros);
        List<Produto> produtos = produtoRepository.findAll();
        CalculadoraIndicadores calculadora = new CalculadoraIndicadores(produtos, LocalDateTime.now(clock));
        return new Contexto(filtro, produtoIds(filtro, produtos), calculadora);
    }

    /** Converte e valida os parâmetros recebidos; erros geram 400 com a mensagem. */
    FiltroDashboard filtro(ParametrosDashboard parametros) {
        ParametrosDashboard p = parametros == null ? ParametrosDashboard.vazios() : parametros;
        LocalDate hoje = LocalDate.now(clock);

        LocalDate fim = data(p.dataFim(), "dataFim");
        LocalDate inicio = data(p.dataInicio(), "dataInicio");
        if (fim == null) {
            fim = inicio != null && inicio.isAfter(hoje) ? inicio : hoje;
        }
        if (inicio == null) {
            inicio = fim.minusDays(DIAS_PADRAO - 1L);
        }
        if (inicio.isAfter(fim)) {
            throw new RegraNegocioException("A data inicial (dataInicio) não pode ser posterior à data final (dataFim).");
        }
        FiltroDashboard filtro = new FiltroDashboard(inicio, fim, status(p.status()),
                enumeracao(p.tipoEntrega(), TipoEntrega.class, null, "tipoEntrega"),
                texto(p.categoria(), "categoria"), texto(p.produtoId(), "produtoId"));
        if (filtro.dias() > LIMITE_PERIODO_DIAS) {
            throw new RegraNegocioException("O período consultado não pode ultrapassar " + LIMITE_PERIODO_DIAS
                    + " dias (informado: " + filtro.dias() + " dias).");
        }
        return filtro;
    }

    /** Produtos que o pedido deve conter; null quando não há filtro de produto/categoria. */
    private static Set<String> produtoIds(FiltroDashboard filtro, List<Produto> produtos) {
        if (filtro.categoria() == null) {
            return filtro.produtoId() == null ? null : Set.of(filtro.produtoId());
        }
        Set<String> ids = produtos.stream()
                .filter(p -> p.getId() != null && p.getTipo() != null && p.getTipo().trim().equalsIgnoreCase(filtro.categoria()))
                .map(Produto::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (filtro.produtoId() != null) {
            ids.retainAll(Set.of(filtro.produtoId()));
        }
        return ids;
    }

    private static List<String> categorias(List<Produto> produtos) {
        // TreeMap sem diferenciar maiúsculas: "pizza" e "Pizza" viram uma única categoria
        Map<String, String> unicas = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Produto produto : produtos) {
            if (!vazio(produto.getTipo())) {
                unicas.putIfAbsent(produto.getTipo().trim(), produto.getTipo().trim());
            }
        }
        return List.copyOf(unicas.values());
    }

    private static LocalDate data(String valor, String parametro) {
        if (vazio(valor)) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new RegraNegocioException("Data inválida no parâmetro '" + parametro + "': '" + valor
                    + "'. Use o formato AAAA-MM-DD, ex.: 2026-11-08.");
        }
    }

    private static Set<StatusPedido> status(List<String> valores) {
        if (valores == null) {
            return Set.of();
        }
        Set<StatusPedido> status = EnumSet.noneOf(StatusPedido.class);
        for (String valor : valores) {
            if (valor == null) {
                continue;
            }
            for (String parte : valor.split(",")) {
                if (!parte.isBlank()) {
                    status.add(enumeracao(parte, StatusPedido.class, null, "status"));
                }
            }
        }
        return status;
    }

    static <E extends Enum<E>> E enumeracao(String valor, Class<E> tipo, E padrao, String parametro) {
        if (vazio(valor)) {
            return padrao;
        }
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            String aceitos = Arrays.stream(tipo.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
            throw new RegraNegocioException("Valor inválido no parâmetro '" + parametro + "': '" + valor
                    + "'. Valores aceitos: " + aceitos + ".");
        }
    }

    private static int limite(Integer limite) {
        if (limite == null) {
            return LIMITE_PADRAO;
        }
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new RegraNegocioException("O parâmetro 'limite' deve estar entre 1 e " + LIMITE_MAXIMO
                    + " (informado: " + limite + ").");
        }
        return limite;
    }

    private static String texto(String valor, String parametro) {
        if (vazio(valor)) {
            return null;
        }
        String limpo = valor.trim();
        if (limpo.length() > TAMANHO_MAXIMO_TEXTO) {
            throw new RegraNegocioException("O parâmetro '" + parametro + "' aceita no máximo "
                    + TAMANHO_MAXIMO_TEXTO + " caracteres.");
        }
        return limpo;
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}

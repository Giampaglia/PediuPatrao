package com.umc.pediupatrao.service.dashboard;

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
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Regras de cálculo dos indicadores do dashboard. Classe pura (sem acesso a
 * banco ou ao Spring), por isso é testada isoladamente.
 *
 * Regras de negócio:
 * <ul>
 *   <li><b>Venda válida</b>: pedido com status reconhecido e diferente de CANCELADO.
 *       Só vendas válidas entram em faturamento, ticket médio, itens vendidos,
 *       séries, ranking e categorias.</li>
 *   <li><b>Faturamento</b>: valor total do pedido, já com desconto. Com filtro de
 *       produto/categoria, considera apenas os itens filtrados, com o desconto do
 *       pedido rateado proporcionalmente entre os itens.</li>
 *   <li><b>Ticket médio</b>: faturamento / quantidade de vendas válidas.</li>
 *   <li><b>Taxa de cancelamento</b>: cancelados / pedidos com status reconhecido.</li>
 *   <li><b>Dados incompletos</b> (documentos antigos): valores nulos contam como
 *       zero e pedidos sem status reconhecido são contados à parte, sem quebrar
 *       os cálculos.</li>
 * </ul>
 */
public class CalculadoraIndicadores {

    public static final String SEM_CATEGORIA = "Sem categoria";

    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final int ESCALA_INTERNA = 10;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final String[] SIGLAS_DIA_SEMANA = {"SEG", "TER", "QUA", "QUI", "SEX", "SAB", "DOM"};
    private static final String[] NOMES_DIA_SEMANA = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};

    private final Map<String, Produto> catalogo = new HashMap<>();
    private final LocalDateTime agora;

    public CalculadoraIndicadores(Collection<Produto> produtos, LocalDateTime agora) {
        if (produtos != null) {
            for (Produto produto : produtos) {
                if (produto != null && produto.getId() != null) {
                    catalogo.put(produto.getId(), produto);
                }
            }
        }
        this.agora = agora;
    }

    // =====================================================================
    // RF01 — indicadores gerais
    // =====================================================================

    public ResumoVendas resumo(FiltroDashboard filtro, List<Pedido> pedidos, List<Pedido> pedidosAnteriores,
            long pedidosSemDataEntrada) {
        Metricas atual = metricas(filtro, pedidos);
        Metricas anterior = metricas(filtro, pedidosAnteriores);

        ResumoVendas.Comparativo comparativo = new ResumoVendas.Comparativo(
                filtro.periodoAnterior().periodo(),
                dinheiro(anterior.faturamento),
                anterior.validos,
                dinheiro(anterior.ticketMedio()),
                variacao(atual.faturamento, anterior.faturamento),
                variacao(BigDecimal.valueOf(atual.validos), BigDecimal.valueOf(anterior.validos)),
                variacao(atual.ticketMedio(), anterior.ticketMedio()));

        return new ResumoVendas(
                filtro.periodo(),
                dinheiro(atual.faturamento),
                atual.total,
                atual.validos,
                atual.cancelados,
                percentual(atual.cancelados, atual.validos + atual.cancelados),
                dinheiro(atual.ticketMedio()),
                atual.itens,
                dinheiro(atual.descontos),
                atual.comDesconto,
                atual.emAndamento,
                atual.tempoMedioAteSaida(),
                comparativo,
                new ResumoVendas.QualidadeDados(atual.semStatus, atual.semValor, pedidosSemDataEntrada),
                agora);
    }

    private Metricas metricas(FiltroDashboard filtro, List<Pedido> pedidos) {
        Metricas m = new Metricas();
        Predicate<ItemPedido> itemAtende = itemAtende(filtro);
        for (Pedido pedido : naoNulos(pedidos)) {
            m.total++;
            StatusPedido status = pedido.getStatus();
            if (pedido.getValorTotal() == null) {
                m.semValor++;
            }
            if (status == null) {
                m.semStatus++;
                continue;
            }
            if (status == StatusPedido.CANCELADO) {
                m.cancelados++;
                continue;
            }
            m.validos++;
            if (!status.isEncerrado()) {
                m.emAndamento++;
            }
            m.faturamento = m.faturamento.add(valorVendido(pedido, filtro, itemAtende));
            m.itens += quantidadeVendida(pedido, itemAtende);
            BigDecimal desconto = descontoVendido(pedido, filtro, itemAtende);
            if (desconto.signum() > 0) {
                m.descontos = m.descontos.add(desconto);
                m.comDesconto++;
            }
            if (pedido.getDataHoraEntrada() != null && pedido.getDataHoraSaida() != null
                    && !pedido.getDataHoraSaida().isBefore(pedido.getDataHoraEntrada())) {
                m.minutosAteSaida += Duration.between(pedido.getDataHoraEntrada(), pedido.getDataHoraSaida()).toSeconds() / 60.0;
                m.pedidosComSaida++;
            }
        }
        return m;
    }

    private static final class Metricas {
        long total;
        long validos;
        long cancelados;
        long semStatus;
        long semValor;
        long emAndamento;
        long itens;
        long comDesconto;
        long pedidosComSaida;
        double minutosAteSaida;
        BigDecimal faturamento = BigDecimal.ZERO;
        BigDecimal descontos = BigDecimal.ZERO;

        BigDecimal ticketMedio() {
            return validos == 0 ? BigDecimal.ZERO
                    : faturamento.divide(BigDecimal.valueOf(validos), ESCALA_INTERNA, RoundingMode.HALF_UP);
        }

        BigDecimal tempoMedioAteSaida() {
            return pedidosComSaida == 0 ? null
                    : BigDecimal.valueOf(minutosAteSaida / pedidosComSaida).setScale(1, RoundingMode.HALF_UP);
        }
    }

    // =====================================================================
    // RF02 — séries de vendas
    // =====================================================================

    public SerieVendas serie(FiltroDashboard filtro, List<Pedido> pedidos, Agrupamento agrupamento) {
        Map<String, Acumulador> pontos = pontosVazios(filtro, agrupamento);
        Predicate<ItemPedido> itemAtende = itemAtende(filtro);
        long totalPedidos = 0;
        BigDecimal totalFaturamento = BigDecimal.ZERO;

        for (Pedido pedido : naoNulos(pedidos)) {
            if (!isVenda(pedido) || pedido.getDataHoraEntrada() == null) {
                continue;
            }
            Acumulador ponto = pontos.get(chave(pedido.getDataHoraEntrada(), agrupamento));
            if (ponto == null) {
                continue; // fora do período (não deveria ocorrer, o banco já filtrou)
            }
            BigDecimal valor = valorVendido(pedido, filtro, itemAtende);
            ponto.pedidos++;
            ponto.faturamento = ponto.faturamento.add(valor);
            totalPedidos++;
            totalFaturamento = totalFaturamento.add(valor);
        }

        List<SerieVendas.Ponto> lista = new ArrayList<>();
        for (Map.Entry<String, Acumulador> entrada : pontos.entrySet()) {
            Acumulador a = entrada.getValue();
            lista.add(new SerieVendas.Ponto(entrada.getKey(), a.rotulo, a.pedidos, dinheiro(a.faturamento),
                    dinheiro(divide(a.faturamento, a.pedidos))));
        }
        return new SerieVendas(agrupamento, filtro.periodo(), totalPedidos, dinheiro(totalFaturamento), lista);
    }

    private static final class Acumulador {
        final String rotulo;
        long pedidos;
        BigDecimal faturamento = BigDecimal.ZERO;

        Acumulador(String rotulo) {
            this.rotulo = rotulo;
        }
    }

    /** Todos os pontos do período, em ordem, para que dias sem venda apareçam com zero. */
    private static Map<String, Acumulador> pontosVazios(FiltroDashboard filtro, Agrupamento agrupamento) {
        Map<String, Acumulador> pontos = new LinkedHashMap<>();
        switch (agrupamento) {
            case DIA -> {
                for (LocalDate d = filtro.dataInicio(); !d.isAfter(filtro.dataFim()); d = d.plusDays(1)) {
                    pontos.put(d.toString(), new Acumulador(d.format(DIA_MES)));
                }
            }
            case SEMANA -> {
                LocalDate inicio = inicioDaSemana(filtro.dataInicio());
                for (LocalDate d = inicio; !d.isAfter(filtro.dataFim()); d = d.plusWeeks(1)) {
                    pontos.put(d.toString(), new Acumulador("Sem. " + d.format(DIA_MES)));
                }
            }
            case MES -> {
                YearMonth fim = YearMonth.from(filtro.dataFim());
                for (YearMonth m = YearMonth.from(filtro.dataInicio()); !m.isAfter(fim); m = m.plusMonths(1)) {
                    String mes = m.getMonth().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "");
                    pontos.put(m.toString(), new Acumulador(mes + "/" + String.format("%02d", m.getYear() % 100)));
                }
            }
            case HORA_DO_DIA -> {
                for (int h = 0; h < 24; h++) {
                    pontos.put(String.valueOf(h), new Acumulador(String.format("%02dh", h)));
                }
            }
            case DIA_DA_SEMANA -> {
                for (int i = 0; i < 7; i++) {
                    pontos.put(SIGLAS_DIA_SEMANA[i], new Acumulador(NOMES_DIA_SEMANA[i]));
                }
            }
        }
        return pontos;
    }

    private static String chave(LocalDateTime dataHora, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> dataHora.toLocalDate().toString();
            case SEMANA -> inicioDaSemana(dataHora.toLocalDate()).toString();
            case MES -> YearMonth.from(dataHora).toString();
            case HORA_DO_DIA -> String.valueOf(dataHora.getHour());
            case DIA_DA_SEMANA -> SIGLAS_DIA_SEMANA[dataHora.getDayOfWeek().getValue() - 1];
        };
    }

    private static LocalDate inicioDaSemana(LocalDate data) {
        return data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    // =====================================================================
    // RF02 — categorias e tipo de entrega
    // =====================================================================

    public VendasPorCategoria categorias(FiltroDashboard filtro, List<Pedido> pedidos) {
        Map<String, long[]> quantidades = new LinkedHashMap<>();
        Map<String, BigDecimal> faturamentos = new LinkedHashMap<>();
        Predicate<ItemPedido> itemAtende = itemAtende(filtro);
        BigDecimal total = BigDecimal.ZERO;

        for (Pedido pedido : vendas(pedidos)) {
            BigDecimal fator = fatorLiquido(pedido);
            for (ItemPedido item : itens(pedido)) {
                if (!itemAtende.test(item)) {
                    continue;
                }
                String categoria = categoriaDe(item);
                BigDecimal valor = valorItem(item, fator);
                quantidades.computeIfAbsent(categoria, c -> new long[1])[0] += item.getQuantidade();
                faturamentos.merge(categoria, valor, BigDecimal::add);
                total = total.add(valor);
            }
        }

        final BigDecimal totalFinal = total;
        List<VendasPorCategoria.Categoria> lista = faturamentos.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new VendasPorCategoria.Categoria(e.getKey(), quantidades.get(e.getKey())[0],
                        dinheiro(e.getValue()), percentual(e.getValue(), totalFinal)))
                .toList();
        return new VendasPorCategoria(filtro.periodo(), dinheiro(total), lista);
    }

    public VendasPorTipoEntrega tiposEntrega(FiltroDashboard filtro, List<Pedido> pedidos) {
        Map<TipoEntrega, Acumulador> porTipo = new LinkedHashMap<>();
        for (TipoEntrega tipo : TipoEntrega.values()) {
            porTipo.put(tipo, new Acumulador(tipo.getDescricao()));
        }
        Predicate<ItemPedido> itemAtende = itemAtende(filtro);
        long total = 0;
        for (Pedido pedido : vendas(pedidos)) {
            Acumulador a = porTipo.get(pedido.getTipoEntrega());
            if (a == null) {
                continue; // pedido antigo sem tipo de entrega
            }
            a.pedidos++;
            a.faturamento = a.faturamento.add(valorVendido(pedido, filtro, itemAtende));
            total++;
        }
        List<VendasPorTipoEntrega.Tipo> tipos = new ArrayList<>();
        for (Map.Entry<TipoEntrega, Acumulador> e : porTipo.entrySet()) {
            Acumulador a = e.getValue();
            tipos.add(new VendasPorTipoEntrega.Tipo(e.getKey(), a.rotulo, a.pedidos, dinheiro(a.faturamento),
                    dinheiro(divide(a.faturamento, a.pedidos)), percentual(a.pedidos, total)));
        }
        return new VendasPorTipoEntrega(filtro.periodo(), total, tipos);
    }

    // =====================================================================
    // RF03 — situação dos pedidos
    // =====================================================================

    public SituacaoPedidos situacao(FiltroDashboard filtro, List<Pedido> pedidos) {
        Map<StatusPedido, Acumulador> porStatus = new LinkedHashMap<>();
        for (StatusPedido status : StatusPedido.values()) {
            porStatus.put(status, new Acumulador(status.getDescricao()));
        }
        Predicate<ItemPedido> itemAtende = itemAtende(filtro);
        long total = 0;
        long emAndamento = 0;
        for (Pedido pedido : naoNulos(pedidos)) {
            StatusPedido status = pedido.getStatus();
            if (status == null) {
                continue; // contado em qualidadeDados.pedidosSemStatus no resumo
            }
            Acumulador a = porStatus.get(status);
            a.pedidos++;
            a.faturamento = a.faturamento.add(valorVendido(pedido, filtro, itemAtende));
            total++;
            if (!status.isEncerrado()) {
                emAndamento++;
            }
        }
        List<SituacaoPedidos.QuantidadePorStatus> lista = new ArrayList<>();
        for (Map.Entry<StatusPedido, Acumulador> e : porStatus.entrySet()) {
            Acumulador a = e.getValue();
            lista.add(new SituacaoPedidos.QuantidadePorStatus(e.getKey(), a.rotulo, a.pedidos,
                    percentual(a.pedidos, total), dinheiro(a.faturamento)));
        }
        return new SituacaoPedidos(filtro.periodo(), total, emAndamento,
                porStatus.get(StatusPedido.FINALIZADO).pedidos, porStatus.get(StatusPedido.CANCELADO).pedidos, lista);
    }

    public List<PedidoRecente> recentes(List<Pedido> pedidos) {
        return naoNulos(pedidos).stream().map(p -> new PedidoRecente(
                p.getId(),
                p.getCodigo(),
                p.getClienteNome(),
                p.getStatus(),
                p.getStatus() == null ? "Status desconhecido" : p.getStatus().getDescricao(),
                p.getTipoEntrega(),
                p.getQuantidadeItens(),
                dinheiro(nz(p.getValorTotal())),
                p.getDataHoraEntrada(),
                p.getDataHoraEntrada() == null || agora == null ? null
                        : Math.max(0, Duration.between(p.getDataHoraEntrada(), agora).toMinutes())))
                .toList();
    }

    // =====================================================================
    // RF04 — produtos mais vendidos
    // =====================================================================

    public RankingProdutos ranking(FiltroDashboard filtro, List<Pedido> pedidos, CriterioRanking criterio, int limite) {
        Map<String, ProdutoAcumulado> porProduto = new LinkedHashMap<>();
        Predicate<ItemPedido> itemAtende = itemAtende(filtro);
        long totalItens = 0;
        BigDecimal totalFaturamento = BigDecimal.ZERO;

        for (Pedido pedido : vendas(pedidos)) {
            BigDecimal fator = fatorLiquido(pedido);
            Set<String> contadosNoPedido = new HashSet<>();
            for (ItemPedido item : itens(pedido)) {
                if (!itemAtende.test(item)) {
                    continue;
                }
                String chave = item.getProdutoId() != null ? item.getProdutoId() : "nome:" + item.getNomeProduto();
                ProdutoAcumulado p = porProduto.computeIfAbsent(chave, k -> new ProdutoAcumulado(item, categoriaDe(item)));
                BigDecimal valor = valorItem(item, fator);
                p.quantidade += item.getQuantidade();
                p.faturamento = p.faturamento.add(valor);
                if (contadosNoPedido.add(chave)) {
                    p.pedidos++;
                }
                totalItens += item.getQuantidade();
                totalFaturamento = totalFaturamento.add(valor);
            }
        }

        Comparator<ProdutoAcumulado> porQuantidade = Comparator.comparingLong(p -> p.quantidade);
        Comparator<ProdutoAcumulado> porFaturamento = Comparator.comparing(p -> p.faturamento);
        Comparator<ProdutoAcumulado> ordem = (criterio == CriterioRanking.FATURAMENTO
                ? porFaturamento.thenComparing(porQuantidade)
                : porQuantidade.thenComparing(porFaturamento)).reversed()
                .thenComparing(p -> p.nome == null ? "" : p.nome);

        final long itensFinal = totalItens;
        final BigDecimal faturamentoFinal = totalFaturamento;
        Function<ProdutoAcumulado, BigDecimal> participacao = criterio == CriterioRanking.FATURAMENTO
                ? p -> percentual(p.faturamento, faturamentoFinal)
                : p -> percentual(p.quantidade, itensFinal);

        List<ProdutoAcumulado> ordenados = porProduto.values().stream().sorted(ordem).limit(limite).toList();
        List<RankingProdutos.ProdutoVendido> lista = new ArrayList<>();
        for (int i = 0; i < ordenados.size(); i++) {
            ProdutoAcumulado p = ordenados.get(i);
            lista.add(new RankingProdutos.ProdutoVendido(i + 1, p.produtoId, p.nome, p.categoria, p.quantidade,
                    dinheiro(p.faturamento), p.pedidos, participacao.apply(p)));
        }
        return new RankingProdutos(filtro.periodo(), criterio, limite, totalItens, dinheiro(totalFaturamento), lista);
    }

    private static final class ProdutoAcumulado {
        final String produtoId;
        final String nome;
        final String categoria;
        long quantidade;
        long pedidos;
        BigDecimal faturamento = BigDecimal.ZERO;

        ProdutoAcumulado(ItemPedido item, String categoria) {
            this.produtoId = item.getProdutoId();
            this.nome = item.getNomeProduto();
            this.categoria = categoria;
        }
    }

    // =====================================================================
    // Apoio
    // =====================================================================

    public static boolean isVenda(Pedido pedido) {
        return pedido != null && pedido.getStatus() != null && pedido.getStatus() != StatusPedido.CANCELADO;
    }

    /** Categoria (tipo) atual do produto no cadastro; produtos excluídos ficam em "Sem categoria". */
    public String categoriaDe(ItemPedido item) {
        Produto produto = item.getProdutoId() == null ? null : catalogo.get(item.getProdutoId());
        if (produto == null || produto.getTipo() == null || produto.getTipo().isBlank()) {
            return SEM_CATEGORIA;
        }
        return produto.getTipo().trim();
    }

    private Predicate<ItemPedido> itemAtende(FiltroDashboard filtro) {
        return item -> (filtro.produtoId() == null || filtro.produtoId().equals(item.getProdutoId()))
                && (filtro.categoria() == null || filtro.categoria().equalsIgnoreCase(categoriaDe(item)));
    }

    /** Valor vendido do pedido: total com desconto, ou só dos itens filtrados (com desconto rateado). */
    private BigDecimal valorVendido(Pedido pedido, FiltroDashboard filtro, Predicate<ItemPedido> itemAtende) {
        if (!filtro.filtraItens()) {
            return nz(pedido.getValorTotal());
        }
        BigDecimal fator = fatorLiquido(pedido);
        BigDecimal soma = BigDecimal.ZERO;
        for (ItemPedido item : itens(pedido)) {
            if (itemAtende.test(item)) {
                soma = soma.add(valorItem(item, fator));
            }
        }
        return soma;
    }

    private BigDecimal descontoVendido(Pedido pedido, FiltroDashboard filtro, Predicate<ItemPedido> itemAtende) {
        BigDecimal desconto = nz(pedido.getValorDesconto());
        if (!filtro.filtraItens() || desconto.signum() == 0) {
            return desconto;
        }
        BigDecimal subtotal = nz(pedido.getSubtotal());
        if (subtotal.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal subtotalFiltrado = BigDecimal.ZERO;
        for (ItemPedido item : itens(pedido)) {
            if (itemAtende.test(item)) {
                subtotalFiltrado = subtotalFiltrado.add(nz(item.getSubtotal()));
            }
        }
        return desconto.multiply(subtotalFiltrado).divide(subtotal, ESCALA_INTERNA, RoundingMode.HALF_UP);
    }

    private static long quantidadeVendida(Pedido pedido, Predicate<ItemPedido> itemAtende) {
        long soma = 0;
        for (ItemPedido item : itens(pedido)) {
            if (itemAtende.test(item)) {
                soma += Math.max(0, item.getQuantidade());
            }
        }
        return soma;
    }

    /** Proporção total/subtotal do pedido, usada para ratear o desconto entre os itens. */
    private static BigDecimal fatorLiquido(Pedido pedido) {
        BigDecimal subtotal = nz(pedido.getSubtotal());
        if (subtotal.signum() == 0) {
            return BigDecimal.ONE;
        }
        return nz(pedido.getValorTotal()).divide(subtotal, ESCALA_INTERNA, RoundingMode.HALF_UP);
    }

    private static BigDecimal valorItem(ItemPedido item, BigDecimal fator) {
        return nz(item.getSubtotal()).multiply(fator);
    }

    private static List<Pedido> vendas(List<Pedido> pedidos) {
        return naoNulos(pedidos).stream().filter(CalculadoraIndicadores::isVenda).toList();
    }

    private static List<Pedido> naoNulos(List<Pedido> pedidos) {
        return pedidos == null ? List.of() : pedidos.stream().filter(p -> p != null).toList();
    }

    private static List<ItemPedido> itens(Pedido pedido) {
        return pedido.getItens() == null ? List.of() : pedido.getItens().stream().filter(i -> i != null).toList();
    }

    static BigDecimal nz(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    static BigDecimal dinheiro(BigDecimal valor) {
        return nz(valor).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal divide(BigDecimal valor, long quantidade) {
        return quantidade == 0 ? BigDecimal.ZERO
                : valor.divide(BigDecimal.valueOf(quantidade), ESCALA_INTERNA, RoundingMode.HALF_UP);
    }

    static BigDecimal percentual(long parte, long total) {
        return percentual(BigDecimal.valueOf(parte), BigDecimal.valueOf(total));
    }

    static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return nz(parte).multiply(CEM).divide(total, 1, RoundingMode.HALF_UP);
    }

    /** Variação percentual em relação ao valor anterior; null quando não há base de comparação. */
    static BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0) {
            return null;
        }
        return nz(atual).subtract(anterior).multiply(CEM).divide(anterior, 1, RoundingMode.HALF_UP);
    }
}

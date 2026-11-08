package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gera um histórico de pedidos para demonstrar o dashboard. Desligado por
 * padrão; para ativar, inicie a aplicação com
 * {@code --pediupatrao.demo.gerar-pedidos=true}.
 *
 * Só gera dados quando a coleção tem menos de 50 pedidos, para nunca
 * misturar dados fictícios com uma operação real. Os pedidos são gravados
 * diretamente pelos repositórios, com os mesmos campos que o PedidoService
 * preencheria (itens, valores, responsáveis, datas e histórico de status).
 */
@Configuration
@ConditionalOnProperty(name = "pediupatrao.demo.gerar-pedidos", havingValue = "true")
public class DadosDemonstracaoConfig {

    private static final Logger log = LoggerFactory.getLogger(DadosDemonstracaoConfig.class);
    static final int MINIMO_PEDIDOS_EXISTENTES = 50;

    private static final Object[][] PRODUTOS = {
            {"Pizza Calabresa", "Pizza", 49.90, 14},
            {"Pizza Mussarela", "Pizza", 45.00, 12},
            {"Pizza Portuguesa", "Pizza", 54.90, 9},
            {"Pizza Frango com Catupiry", "Pizza", 56.90, 10},
            {"Pizza Quatro Queijos", "Pizza", 58.90, 6},
            {"Pizza Margherita", "Pizza", 52.00, 5},
            {"Esfiha de Carne", "Esfiha", 7.50, 8},
            {"Esfiha de Queijo", "Esfiha", 7.00, 6},
            {"X-Burger", "Lanche", 24.90, 6},
            {"X-Salada", "Lanche", 27.90, 4},
            {"Refrigerante 2L", "Bebida", 12.00, 13},
            {"Suco Natural 1L", "Bebida", 15.00, 4},
            {"Água Mineral 500ml", "Bebida", 4.00, 3},
    };

    private static final String[] CLIENTES = {
            "Ana Lima", "Bruno Costa", "Carla Mendes", "Diego Santos", "Eduarda Rocha", "Felipe Alves",
            "Gabriela Nunes", "Henrique Dias", "Isabela Moura", "João Pereira", "Karina Teixeira",
            "Lucas Martins", "Mariana Freitas", "Nicolas Ribeiro", "Olívia Cardoso"};

    @Bean
    CommandLineRunner gerarPedidosDeDemonstracao(PedidoRepository pedidos, ProdutoRepository produtos,
            ClienteRepository clientes, Clock clock,
            @Value("${pediupatrao.demo.dias:90}") int dias) {
        return args -> {
            long existentes = pedidos.count();
            if (existentes >= MINIMO_PEDIDOS_EXISTENTES) {
                log.info("Dados de demonstração não gerados: já existem {} pedidos.", existentes);
                return;
            }
            List<Produto> catalogo = garantirProdutos(produtos);
            List<Cliente> listaClientes = garantirClientes(clientes);
            List<Pedido> gerados = gerar(catalogo, listaClientes, LocalDateTime.now(clock), dias, new Random(2026));
            pedidos.saveAll(gerados);
            log.info("{} pedidos de demonstração gerados para os últimos {} dias.", gerados.size(), dias);
        };
    }

    private static List<Produto> garantirProdutos(ProdutoRepository repositorio) {
        Map<String, Produto> porNome = repositorio.findAll().stream()
                .filter(p -> p.getNome() != null)
                .collect(Collectors.toMap(p -> p.getNome().toLowerCase(), Function.identity(), (a, b) -> a));
        List<Produto> catalogo = new ArrayList<>();
        for (Object[] dados : PRODUTOS) {
            Produto produto = porNome.get(((String) dados[0]).toLowerCase());
            if (produto == null) {
                produto = new Produto();
                produto.setNome((String) dados[0]);
                produto.setTipo((String) dados[1]);
                produto.setPreco((Double) dados[2]);
                produto.setAtivo(true);
                produto = repositorio.save(produto);
            }
            if (produto.getPreco() != null && !Boolean.FALSE.equals(produto.getAtivo())) {
                catalogo.add(produto);
            }
        }
        return catalogo;
    }

    private static List<Cliente> garantirClientes(ClienteRepository repositorio) {
        List<Cliente> existentes = repositorio.findAll();
        if (existentes.size() >= 10) {
            return existentes;
        }
        List<Cliente> novos = new ArrayList<>();
        for (int i = 0; i < CLIENTES.length; i++) {
            Cliente c = new Cliente();
            c.setNome(CLIENTES[i]);
            c.setTelefone(String.format("(11) 9%04d-%04d", 1000 + i * 37, 2000 + i * 53));
            c.setLogradouro("Rua das Acácias");
            c.setNumero(String.valueOf(100 + i * 10));
            c.setBairro("Centro");
            c.setCidade("Mogi das Cruzes");
            c.setEstado("SP");
            c.setCep("08710-000");
            novos.add(c);
        }
        List<Cliente> todos = new ArrayList<>(existentes);
        todos.addAll(repositorio.saveAll(novos));
        return todos;
    }

    /** Gera pedidos com sazonalidade realista: mais movimento à noite e nos fins de semana. */
    static List<Pedido> gerar(List<Produto> catalogo, List<Cliente> clientes, LocalDateTime agora, int dias, Random random) {
        List<Pedido> lista = new ArrayList<>();
        LocalDate hoje = agora.toLocalDate();
        for (int d = dias - 1; d >= 0; d--) {
            LocalDate data = hoje.minusDays(d);
            DayOfWeek dia = data.getDayOfWeek();
            double fator = switch (dia) {
                case FRIDAY -> 1.4;
                case SATURDAY -> 1.7;
                case SUNDAY -> 1.5;
                case MONDAY -> 0.6;
                default -> 0.9;
            };
            // leve crescimento ao longo do período, para o comparativo mostrar tendência
            double crescimento = 0.85 + 0.3 * (dias - d) / (double) dias;
            int quantidade = (int) Math.round((10 + random.nextInt(6)) * fator * crescimento);
            for (int i = 0; i < quantidade; i++) {
                LocalDateTime entrada = data.atTime(hora(random), random.nextInt(60), random.nextInt(60));
                if (entrada.isAfter(agora)) {
                    continue;
                }
                lista.add(pedido(catalogo, clientes, entrada, agora, random));
            }
        }
        return lista;
    }

    private static int hora(Random random) {
        double r = random.nextDouble();
        if (r < 0.22) {
            return 11 + random.nextInt(3);   // almoço: 11h–13h
        }
        if (r < 0.30) {
            return 15 + random.nextInt(3);   // tarde: 15h–17h
        }
        if (r < 0.92) {
            return 18 + random.nextInt(4);   // jantar (pico): 18h–21h
        }
        return 22 + random.nextInt(2);       // fim de noite: 22h–23h
    }

    private static Pedido pedido(List<Produto> catalogo, List<Cliente> clientes, LocalDateTime entrada,
            LocalDateTime agora, Random random) {
        Cliente cliente = clientes.get(random.nextInt(clientes.size()));
        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setTipoEntrega(random.nextDouble() < 0.62 ? TipoEntrega.ENTREGA : TipoEntrega.RETIRADA);
        String atendente = random.nextBoolean() ? "atendente01" : "gerente01";

        List<ItemPedido> itens = new ArrayList<>();
        int tipos = 1 + random.nextInt(3);
        for (int i = 0; i < tipos; i++) {
            Produto produto = sortearProduto(catalogo, random);
            boolean repetido = itens.stream().anyMatch(it -> it.getProdutoId().equals(produto.getId()));
            if (!repetido) {
                int qtd = "Esfiha".equals(produto.getTipo()) ? 2 + random.nextInt(6) : 1 + (random.nextDouble() < 0.2 ? 1 : 0);
                itens.add(ItemPedido.de(produto, qtd));
            }
        }
        pedido.setItens(itens);
        pedido.recalcularValores();

        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setDataHoraEntrada(entrada);
        pedido.setResponsavelEntrada(atendente);
        pedido.registrarHistorico(StatusPedido.RECEBIDO, entrada, atendente);

        if (random.nextDouble() < 0.10) {
            pedido.setPercentualDesconto(BigDecimal.valueOf(5 + random.nextInt(4) * 5)); // 5, 10, 15 ou 20%
            pedido.setDescontoAplicadoPor("gerente01");
            pedido.setDataHoraDesconto(entrada.plusMinutes(2));
            pedido.setMotivoDesconto("Cliente frequente");
            pedido.recalcularValores();
        }

        if (random.nextDouble() < 0.06) {
            LocalDateTime cancelamento = entrada.plusMinutes(5 + random.nextInt(15));
            if (!cancelamento.isAfter(agora)) {
                pedido.setStatus(StatusPedido.CANCELADO);
                pedido.setDataHoraCancelamento(cancelamento);
                pedido.setResponsavelCancelamento("gerente01");
                pedido.setJustificativaCancelamento("Cliente desistiu do pedido por telefone");
                pedido.registrarHistorico(StatusPedido.CANCELADO, cancelamento, "gerente01");
                return pedido;
            }
        }

        // Avança no fluxo conforme o tempo decorrido até "agora"
        LocalDateTime t = entrada;
        StatusPedido[] fluxo = pedido.getTipoEntrega() == TipoEntrega.ENTREGA
                ? new StatusPedido[]{StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO, StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.FINALIZADO}
                : new StatusPedido[]{StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO, StatusPedido.RETIRADO, StatusPedido.FINALIZADO};
        int[] minutos = {3 + random.nextInt(5), 15 + random.nextInt(15), 3 + random.nextInt(10), 15 + random.nextInt(25)};
        for (int i = 0; i < fluxo.length; i++) {
            t = t.plusMinutes(minutos[i]);
            if (t.isAfter(agora)) {
                break;
            }
            pedido.setStatus(fluxo[i]);
            pedido.registrarHistorico(fluxo[i], t, atendente);
            if (fluxo[i].isSaida()) {
                pedido.setDataHoraSaida(t);
                pedido.setResponsavelSaida(atendente);
            }
            if (fluxo[i] == StatusPedido.FINALIZADO) {
                pedido.setDataHoraFinalizacao(t);
                pedido.setResponsavelFinalizacao(atendente);
            }
        }
        return pedido;
    }

    private static Produto sortearProduto(List<Produto> catalogo, Random random) {
        int total = 0;
        for (Produto p : catalogo) {
            total += peso(p);
        }
        int r = random.nextInt(total);
        for (Produto p : catalogo) {
            r -= peso(p);
            if (r < 0) {
                return p;
            }
        }
        return catalogo.get(0);
    }

    private static int peso(Produto produto) {
        for (Object[] dados : PRODUTOS) {
            if (((String) dados[0]).equalsIgnoreCase(produto.getNome())) {
                return (Integer) dados[3];
            }
        }
        return 3;
    }
}

package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class PedidoService {

    public static final BigDecimal DESCONTO_MAXIMO = new BigDecimal("20");
    static final int MAX_ITENS = 50;
    static final int MAX_QUANTIDADE = 99;
    static final int MIN_JUSTIFICATIVA = 10;
    static final int MAX_TEXTO = 500;

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;
    private final UsuarioLogado usuarioLogado;

    public PedidoService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository,
            ClienteRepository clienteRepository, AuditoriaService auditoriaService, UsuarioLogado usuarioLogado) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
        this.usuarioLogado = usuarioLogado;
    }

    // ========================
    // CONSULTAS
    // ========================
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll(Sort.by(Sort.Direction.DESC, "dataHoraEntrada"));
    }

    public Pedido buscarPorId(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    // ========================
    // ENTRADA (RECEBIDO)
    // ========================
    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE')")
    public Pedido criarPedido(NovoPedidoRequest dados) {
        if (dados == null) {
            throw new RegraNegocioException("Dados do pedido não informados.");
        }
        if (vazio(dados.getClienteId())) {
            throw new RegraNegocioException("Informe o cliente do pedido.");
        }
        Cliente cliente = clienteRepository.findById(dados.getClienteId())
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado."));
        if (dados.getTipoEntrega() == null) {
            throw new RegraNegocioException("Informe se o pedido é para entrega ou retirada.");
        }
        if (dados.getTipoEntrega() == TipoEntrega.ENTREGA && !possuiEndereco(cliente)) {
            throw new RegraNegocioException("O cliente não possui endereço cadastrado: não é possível registrar pedido para entrega.");
        }

        List<ItemPedido> itens = montarItens(dados.getItens());

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setTipoEntrega(dados.getTipoEntrega());
        pedido.setObservacao(textoLimitado(dados.getObservacao(), "A observação"));
        pedido.setItens(itens);
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        pedido.recalcularValores();

        LocalDateTime agora = LocalDateTime.now();
        String usuario = usuarioLogado.username();
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setDataHoraEntrada(agora);
        pedido.setResponsavelEntrada(usuario);
        pedido.registrarHistorico(StatusPedido.RECEBIDO, agora, usuario);

        Pedido salvo = pedidoRepository.save(pedido);

        auditoriaService.registrar(TipoOperacao.INCLUSAO_PEDIDO, AuditoriaService.ENTIDADE_PEDIDO, salvo.getId(),
                new ComparadorCampos()
                        .registrar("cliente", cliente.getNome())
                        .registrar("tipoEntrega", salvo.getTipoEntrega())
                        .registrar("itens", itens.stream().map(ItemPedido::toString).collect(Collectors.joining("; ")))
                        .registrar("subtotal", salvo.getSubtotal())
                        .registrar("valorTotal", salvo.getValorTotal())
                        .registrar("status", salvo.getStatus())
                        .registrar("dataHoraEntrada", agora)
                        .registrar("responsavelEntrada", usuario)
                        .lista(),
                null);
        return salvo;
    }

    private List<ItemPedido> montarItens(List<ItemPedidoRequest> solicitados) {
        // Linhas sem produto (ex.: linha vazia do formulário) são ignoradas
        List<ItemPedidoRequest> preenchidos = solicitados == null ? List.of()
                : solicitados.stream().filter(i -> i != null && !vazio(i.getProdutoId())).toList();

        if (preenchidos.isEmpty()) {
            throw new RegraNegocioException("O pedido deve ter ao menos um item.");
        }
        if (preenchidos.size() > MAX_ITENS) {
            throw new RegraNegocioException("O pedido pode ter no máximo " + MAX_ITENS + " itens.");
        }

        List<ItemPedido> itens = new ArrayList<>();
        for (ItemPedidoRequest solicitado : preenchidos) {
            Produto produto = produtoRepository.findById(solicitado.getProdutoId())
                    .orElseThrow(() -> new RegraNegocioException("Produto não encontrado: " + solicitado.getProdutoId()));
            Integer quantidade = solicitado.getQuantidade();
            if (quantidade == null || quantidade < 1 || quantidade > MAX_QUANTIDADE) {
                throw new RegraNegocioException("Quantidade inválida para \"" + produto.getNome()
                        + "\": informe um valor entre 1 e " + MAX_QUANTIDADE + ".");
            }
            if (Boolean.FALSE.equals(produto.getAtivo())) {
                throw new RegraNegocioException("O produto \"" + produto.getNome() + "\" está inativo.");
            }
            if (produto.getPreco() == null || produto.getPreco() <= 0) {
                throw new RegraNegocioException("O produto \"" + produto.getNome() + "\" não possui preço válido.");
            }
            itens.add(ItemPedido.de(produto, quantidade));
        }
        return itens;
    }

    // ========================
    // TRANSIÇÕES DE STATUS
    // ========================
    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE')")
    public Pedido atualizarStatus(String id, String status) {
        StatusPedido destino = StatusPedido.deTexto(status)
                .orElseThrow(() -> new RegraNegocioException("Status inválido: " + status));
        return alterarStatus(id, destino);
    }

    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE')")
    public Pedido alterarStatus(String id, StatusPedido destino) {
        Pedido pedido = buscarPorId(id);
        StatusPedido atual = pedido.getStatus();

        if (atual == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode mais ser alterado.");
        }
        if (destino == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("O cancelamento deve ser feito pela operação de cancelamento, "
                    + "exclusiva do gerente e com justificativa.");
        }
        if (atual == null) {
            throw new RegraNegocioException("Pedido sem status definido.");
        }
        if (!atual.podeIrPara(destino, pedido.getTipoEntrega())) {
            String permitidos = atual.proximos(pedido.getTipoEntrega()).stream()
                    .map(StatusPedido::getDescricao).collect(Collectors.joining(", "));
            throw new RegraNegocioException("Transição inválida: " + atual.getDescricao() + " → "
                    + (destino == null ? "?" : destino.getDescricao())
                    + (permitidos.isEmpty() ? ". O pedido já foi encerrado." : ". Próximo status permitido: " + permitidos + "."));
        }

        LocalDateTime agora = LocalDateTime.now();
        String usuario = usuarioLogado.username();
        ComparadorCampos alteracoes = new ComparadorCampos().comparar("status", atual, destino);

        pedido.setStatus(destino);
        if (destino.isSaida()) {
            pedido.setDataHoraSaida(agora);
            pedido.setResponsavelSaida(usuario);
            alteracoes.comparar("dataHoraSaida", null, agora).comparar("responsavelSaida", null, usuario);
        }
        if (destino == StatusPedido.FINALIZADO) {
            pedido.setDataHoraFinalizacao(agora);
            pedido.setResponsavelFinalizacao(usuario);
            alteracoes.comparar("dataHoraFinalizacao", null, agora).comparar("responsavelFinalizacao", null, usuario);
        }
        pedido.registrarHistorico(destino, agora, usuario);

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.ALTERACAO_STATUS_PEDIDO, AuditoriaService.ENTIDADE_PEDIDO,
                salvo.getId(), alteracoes.lista(), null);
        return salvo;
    }

    // ========================
    // DESCONTO (exclusivo do GERENTE)
    // ========================
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual, String motivo) {
        if (percentual == null) {
            throw new RegraNegocioException("Informe o percentual de desconto.");
        }
        BigDecimal pct = percentual.setScale(2, RoundingMode.HALF_UP);
        if (pct.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("O percentual de desconto deve ser maior que zero.");
        }
        if (pct.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new RegraNegocioException("O desconto máximo permitido é de " + DESCONTO_MAXIMO + "% do valor do pedido.");
        }

        Pedido pedido = buscarPorId(id);
        validarAntesDaSaida(pedido, "aplicar desconto");

        BigDecimal percentualAnterior = pedido.getPercentualDesconto();
        BigDecimal descontoAnterior = pedido.getValorDesconto();
        BigDecimal totalAnterior = pedido.getValorTotal();

        String motivoLimpo = textoLimitado(motivo, "O motivo");
        pedido.setPercentualDesconto(pct);
        pedido.recalcularValores();
        pedido.setDescontoAplicadoPor(usuarioLogado.username());
        pedido.setDataHoraDesconto(LocalDateTime.now());
        pedido.setMotivoDesconto(motivoLimpo);

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.APLICACAO_DESCONTO, AuditoriaService.ENTIDADE_PEDIDO, salvo.getId(),
                new ComparadorCampos()
                        .comparar("percentualDesconto", percentualAnterior, salvo.getPercentualDesconto())
                        .comparar("valorDesconto", descontoAnterior, salvo.getValorDesconto())
                        .comparar("valorTotal", totalAnterior, salvo.getValorTotal())
                        .lista(),
                motivoLimpo);
        return salvo;
    }

    // ========================
    // CANCELAMENTO (exclusivo do GERENTE)
    // ========================
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        String texto = justificativa == null ? "" : justificativa.trim();
        if (texto.length() < MIN_JUSTIFICATIVA) {
            throw new RegraNegocioException("Informe a justificativa do cancelamento (mínimo de "
                    + MIN_JUSTIFICATIVA + " caracteres).");
        }
        if (texto.length() > MAX_TEXTO) {
            throw new RegraNegocioException("A justificativa deve ter no máximo " + MAX_TEXTO + " caracteres.");
        }

        Pedido pedido = buscarPorId(id);
        validarAntesDaSaida(pedido, "cancelar o pedido");

        LocalDateTime agora = LocalDateTime.now();
        String usuario = usuarioLogado.username();
        StatusPedido anterior = pedido.getStatus();

        // Itens, valores e registros de entrada são preservados
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setDataHoraCancelamento(agora);
        pedido.setResponsavelCancelamento(usuario);
        pedido.setJustificativaCancelamento(texto);
        pedido.registrarHistorico(StatusPedido.CANCELADO, agora, usuario);

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.CANCELAMENTO_PEDIDO, AuditoriaService.ENTIDADE_PEDIDO, salvo.getId(),
                new ComparadorCampos()
                        .comparar("status", anterior, StatusPedido.CANCELADO)
                        .comparar("dataHoraCancelamento", null, agora)
                        .comparar("responsavelCancelamento", null, usuario)
                        .lista(),
                texto);
        return salvo;
    }

    private void validarAntesDaSaida(Pedido pedido, String acao) {
        StatusPedido status = pedido.getStatus();
        if (status == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode mais ser alterado.");
        }
        if (status == null || !status.isAntesDaSaida()) {
            throw new RegraNegocioException("Não é possível " + acao + ": o pedido já saiu para entrega ou retirada"
                    + (status == null ? "." : " (status atual: " + status.getDescricao() + ")."));
        }
    }

    private static boolean possuiEndereco(Cliente cliente) {
        return !vazio(cliente.getLogradouro()) || !vazio(cliente.getEndereco());
    }

    private static String textoLimitado(String texto, String nomeCampo) {
        if (vazio(texto)) {
            return null;
        }
        String limpo = texto.trim();
        if (limpo.length() > MAX_TEXTO) {
            throw new RegraNegocioException(nomeCampo + " deve ter no máximo " + MAX_TEXTO + " caracteres.");
        }
        return limpo;
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}

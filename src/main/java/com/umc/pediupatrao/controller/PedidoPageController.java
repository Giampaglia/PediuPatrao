package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Telas de pedidos (Thymeleaf). As regras ficam no PedidoService. */
@Controller
@RequestMapping("/pedidos")
public class PedidoPageController {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final AuditoriaService auditoriaService;
    private final UsuarioLogado usuarioLogado;

    public PedidoPageController(PedidoService pedidoService, ClienteService clienteService,
            ProdutoService produtoService, AuditoriaService auditoriaService, UsuarioLogado usuarioLogado) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.produtoService = produtoService;
        this.auditoriaService = auditoriaService;
        this.usuarioLogado = usuarioLogado;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("pedidos", pedidoService.listarPedidos());
        model.addAttribute("content", "pedidos :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        model.addAttribute("pedido", new NovoPedidoRequest());
        return formulario(model);
    }

    @PostMapping
    public String criar(@ModelAttribute("pedido") NovoPedidoRequest dados, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Pedido pedido = pedidoService.criarPedido(dados);
            redirectAttributes.addFlashAttribute("sucesso", "Pedido #" + pedido.getCodigo() + " registrado. Total: R$ "
                    + pedido.getValorTotal());
            return "redirect:/pedidos/" + pedido.getId();
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return formulario(model);
        }
    }

    @GetMapping("/{id}")
    public String detalhe(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("pedido", pedidoService.buscarPorId(id));
        } catch (RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos";
        }
        if (usuarioLogado.temPerfil(Perfil.ADMIN) || usuarioLogado.temPerfil(Perfil.GERENTE)) {
            model.addAttribute("auditoria", auditoriaService.historicoDe(AuditoriaService.ENTIDADE_PEDIDO, id));
        }
        model.addAttribute("descontoMaximo", PedidoService.DESCONTO_MAXIMO);
        model.addAttribute("content", "pedidos/detalhe :: content");
        return "layout";
    }

    @PostMapping("/{id}/status")
    public String alterarStatus(@PathVariable String id, @RequestParam String status,
            RedirectAttributes redirectAttributes) {
        return executar(id, redirectAttributes, () -> {
            Pedido pedido = pedidoService.atualizarStatus(id, status);
            return "Status alterado para \"" + pedido.getStatus().getDescricao() + "\".";
        });
    }

    @PostMapping("/{id}/desconto")
    public String aplicarDesconto(@PathVariable String id,
            @RequestParam(required = false) String percentual,
            @RequestParam(required = false) String motivo,
            RedirectAttributes redirectAttributes) {
        return executar(id, redirectAttributes, () -> {
            Pedido pedido = pedidoService.aplicarDesconto(id, converterPercentual(percentual), motivo);
            return "Desconto de " + pedido.getPercentualDesconto() + "% aplicado. Novo total: R$ " + pedido.getValorTotal();
        });
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable String id,
            @RequestParam(required = false) String justificativa,
            RedirectAttributes redirectAttributes) {
        return executar(id, redirectAttributes, () -> {
            pedidoService.cancelar(id, justificativa);
            return "Pedido cancelado.";
        });
    }

    private String formulario(Model model) {
        List<Produto> produtosDisponiveis = produtoService.listarProdutos().stream()
                .filter(p -> !Boolean.FALSE.equals(p.getAtivo()) && p.getPreco() != null && p.getPreco() > 0)
                .toList();
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtosDisponiveis);
        model.addAttribute("tiposEntrega", TipoEntrega.values());
        model.addAttribute("content", "pedidos/form :: content");
        return "layout";
    }

    private String executar(String id, RedirectAttributes redirectAttributes, Operacao operacao) {
        try {
            redirectAttributes.addFlashAttribute("sucesso", operacao.executar());
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    private static BigDecimal converterPercentual(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(valor.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new RegraNegocioException("Percentual de desconto inválido.");
        }
    }

    @FunctionalInterface
    private interface Operacao {
        String executar();
    }
}

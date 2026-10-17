package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pedidos")
public class Pedido {

    @Id
    private String id;

    // Cliente (nome copiado para manter o histórico mesmo se o cadastro mudar)
    private String clienteId;
    private String clienteNome;

    private TipoEntrega tipoEntrega;
    private String observacao;
    private List<ItemPedido> itens = new ArrayList<>();

    // Valores — sempre calculados no servidor (ver recalcularValores)
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal percentualDesconto = BigDecimal.ZERO;
    private BigDecimal valorDesconto = BigDecimal.ZERO;
    private BigDecimal valorTotal = BigDecimal.ZERO;
    private String descontoAplicadoPor;
    private LocalDateTime dataHoraDesconto;
    private String motivoDesconto;

    private StatusPedido status;

    // Entrada
    private LocalDateTime dataHoraEntrada;
    private String responsavelEntrada;

    // Saída (saiu para entrega ou retirado)
    private LocalDateTime dataHoraSaida;
    private String responsavelSaida;

    // Finalização
    private LocalDateTime dataHoraFinalizacao;
    private String responsavelFinalizacao;

    // Cancelamento
    private LocalDateTime dataHoraCancelamento;
    private String responsavelCancelamento;
    private String justificativaCancelamento;

    private List<HistoricoStatus> historico = new ArrayList<>();

    /**
     * Recalcula subtotal, desconto e total a partir dos itens e do percentual de
     * desconto autorizado. Nenhum valor monetário vem da requisição HTTP.
     */
    public void recalcularValores() {
        BigDecimal soma = BigDecimal.ZERO;
        if (itens != null) {
            for (ItemPedido item : itens) {
                soma = soma.add(item.getSubtotal());
            }
        }
        BigDecimal percentual = percentualDesconto == null ? BigDecimal.ZERO : percentualDesconto;
        subtotal = soma.setScale(2, RoundingMode.HALF_UP);
        valorDesconto = subtotal.multiply(percentual)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        valorTotal = subtotal.subtract(valorDesconto);
    }

    public void registrarHistorico(StatusPedido novoStatus, LocalDateTime dataHora, String usuario) {
        if (historico == null) {
            historico = new ArrayList<>();
        }
        historico.add(new HistoricoStatus(novoStatus, dataHora, usuario));
    }

    /** Próximos status do fluxo normal, usados pela tela de detalhes. */
    public Set<StatusPedido> getProximosStatus() {
        return status == null ? Set.of() : status.proximos(tipoEntrega);
    }

    /** Código curto para exibição (últimos 6 caracteres do id). */
    public String getCodigo() {
        if (id == null) {
            return "";
        }
        return id.length() <= 6 ? id.toUpperCase() : id.substring(id.length() - 6).toUpperCase();
    }

    public int getQuantidadeItens() {
        if (itens == null) {
            return 0;
        }
        return itens.stream().mapToInt(ItemPedido::getQuantidade).sum();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public String getClienteNome() {
        return clienteNome;
    }

    public void setClienteNome(String clienteNome) {
        this.clienteNome = clienteNome;
    }

    public TipoEntrega getTipoEntrega() {
        return tipoEntrega;
    }

    public void setTipoEntrega(TipoEntrega tipoEntrega) {
        this.tipoEntrega = tipoEntrega;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public List<ItemPedido> getItens() {
        return itens;
    }

    public void setItens(List<ItemPedido> itens) {
        this.itens = itens;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getPercentualDesconto() {
        return percentualDesconto;
    }

    public void setPercentualDesconto(BigDecimal percentualDesconto) {
        this.percentualDesconto = percentualDesconto;
    }

    public BigDecimal getValorDesconto() {
        return valorDesconto;
    }

    public void setValorDesconto(BigDecimal valorDesconto) {
        this.valorDesconto = valorDesconto;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }

    public String getDescontoAplicadoPor() {
        return descontoAplicadoPor;
    }

    public void setDescontoAplicadoPor(String descontoAplicadoPor) {
        this.descontoAplicadoPor = descontoAplicadoPor;
    }

    public LocalDateTime getDataHoraDesconto() {
        return dataHoraDesconto;
    }

    public void setDataHoraDesconto(LocalDateTime dataHoraDesconto) {
        this.dataHoraDesconto = dataHoraDesconto;
    }

    public String getMotivoDesconto() {
        return motivoDesconto;
    }

    public void setMotivoDesconto(String motivoDesconto) {
        this.motivoDesconto = motivoDesconto;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public void setStatus(StatusPedido status) {
        this.status = status;
    }

    public LocalDateTime getDataHoraEntrada() {
        return dataHoraEntrada;
    }

    public void setDataHoraEntrada(LocalDateTime dataHoraEntrada) {
        this.dataHoraEntrada = dataHoraEntrada;
    }

    public String getResponsavelEntrada() {
        return responsavelEntrada;
    }

    public void setResponsavelEntrada(String responsavelEntrada) {
        this.responsavelEntrada = responsavelEntrada;
    }

    public LocalDateTime getDataHoraSaida() {
        return dataHoraSaida;
    }

    public void setDataHoraSaida(LocalDateTime dataHoraSaida) {
        this.dataHoraSaida = dataHoraSaida;
    }

    public String getResponsavelSaida() {
        return responsavelSaida;
    }

    public void setResponsavelSaida(String responsavelSaida) {
        this.responsavelSaida = responsavelSaida;
    }

    public LocalDateTime getDataHoraFinalizacao() {
        return dataHoraFinalizacao;
    }

    public void setDataHoraFinalizacao(LocalDateTime dataHoraFinalizacao) {
        this.dataHoraFinalizacao = dataHoraFinalizacao;
    }

    public String getResponsavelFinalizacao() {
        return responsavelFinalizacao;
    }

    public void setResponsavelFinalizacao(String responsavelFinalizacao) {
        this.responsavelFinalizacao = responsavelFinalizacao;
    }

    public LocalDateTime getDataHoraCancelamento() {
        return dataHoraCancelamento;
    }

    public void setDataHoraCancelamento(LocalDateTime dataHoraCancelamento) {
        this.dataHoraCancelamento = dataHoraCancelamento;
    }

    public String getResponsavelCancelamento() {
        return responsavelCancelamento;
    }

    public void setResponsavelCancelamento(String responsavelCancelamento) {
        this.responsavelCancelamento = responsavelCancelamento;
    }

    public String getJustificativaCancelamento() {
        return justificativaCancelamento;
    }

    public void setJustificativaCancelamento(String justificativaCancelamento) {
        this.justificativaCancelamento = justificativaCancelamento;
    }

    public List<HistoricoStatus> getHistorico() {
        return historico;
    }

    public void setHistorico(List<HistoricoStatus> historico) {
        this.historico = historico;
    }
}

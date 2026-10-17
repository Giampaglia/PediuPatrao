package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Item do pedido. Nome e preço do produto são copiados no momento do registro
 * para que alterações futuras no cadastro de produtos não mudem pedidos antigos.
 */
public class ItemPedido {

    private String produtoId;
    private String nomeProduto;
    private int quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal subtotal;

    public ItemPedido() {
    }

    public static ItemPedido de(Produto produto, int quantidade) {
        ItemPedido item = new ItemPedido();
        item.produtoId = produto.getId();
        item.nomeProduto = produto.getNome();
        item.quantidade = quantidade;
        item.precoUnitario = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
        item.subtotal = item.precoUnitario.multiply(BigDecimal.valueOf(quantidade));
        return item;
    }

    public String getProdutoId() {
        return produtoId;
    }

    public void setProdutoId(String produtoId) {
        this.produtoId = produtoId;
    }

    public String getNomeProduto() {
        return nomeProduto;
    }

    public void setNomeProduto(String nomeProduto) {
        this.nomeProduto = nomeProduto;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    public void setPrecoUnitario(BigDecimal precoUnitario) {
        this.precoUnitario = precoUnitario;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    @Override
    public String toString() {
        return quantidade + "x " + nomeProduto + " (R$ " + precoUnitario + ")";
    }
}

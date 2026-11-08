package com.umc.pediupatrao.dto.dashboard;

import java.util.List;

/**
 * Parâmetros de consulta comuns a todos os endpoints do dashboard, recebidos
 * como texto (query string). A conversão e a validação ficam no
 * DashboardService para que os erros tragam mensagens claras ao cliente.
 *
 * @param dataInicio  data inicial (AAAA-MM-DD), inclusiva
 * @param dataFim     data final (AAAA-MM-DD), inclusiva
 * @param status      status do pedido; aceita o parâmetro repetido ou valores separados por vírgula
 * @param tipoEntrega ENTREGA ou RETIRADA
 * @param categoria   categoria (tipo) do produto, ex.: Pizza
 * @param produtoId   identificador do produto
 */
public record ParametrosDashboard(
        String dataInicio,
        String dataFim,
        List<String> status,
        String tipoEntrega,
        String categoria,
        String produtoId) {

    public static ParametrosDashboard vazios() {
        return new ParametrosDashboard(null, null, null, null, null, null);
    }
}

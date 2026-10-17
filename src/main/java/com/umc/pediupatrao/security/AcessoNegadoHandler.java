package com.umc.pediupatrao.security;

import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * Trata tentativas de acesso não autorizado de usuários autenticados:
 * registra na auditoria/log e responde 403 (JSON nas APIs, página de erro nas telas).
 */
@Component
public class AcessoNegadoHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(AcessoNegadoHandler.class);

    private final AuditoriaService auditoriaService;

    public AcessoNegadoHandler(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        String motivo = exception instanceof CsrfException
                ? "Token CSRF ausente ou inválido"
                : "Perfil sem permissão para a operação";
        String caminho = caminho(request);
        String recurso = request.getMethod() + " " + caminho;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Somente usuário e recurso: nada de cabeçalhos, cookies ou corpo (podem conter credenciais)
        log.warn("Acesso negado: usuario={} recurso={} motivo={}", auth == null ? "anônimo" : auth.getName(), recurso, motivo);
        try {
            auditoriaService.registrar(TipoOperacao.ACESSO_NEGADO, AuditoriaService.ENTIDADE_SISTEMA, recurso, List.of(), motivo);
        } catch (RuntimeException e) {
            log.error("Falha ao registrar acesso negado na auditoria", e);
        }

        if (isApi(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":403,\"erro\":\"Forbidden\",\"mensagem\":\"Acesso negado: "
                    + motivo + ".\",\"caminho\":\"" + caminho.replace("\"", "") + "\"}");
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Acesso negado");
        }
    }

    /** Caminho da requisição sem o context path, ex.: /api/pedidos/123/cancelar. */
    public static String caminho(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    public static boolean isApi(HttpServletRequest request) {
        return caminho(request).startsWith("/api/");
    }
}

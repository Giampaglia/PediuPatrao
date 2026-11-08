package com.umc.pediupatrao.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Converte exceções das APIs REST em respostas JSON padronizadas.
 * AccessDeniedException não é tratada aqui de propósito: ela segue para o
 * Spring Security, que responde 403 e registra a tentativa na auditoria.
 */
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Map<String, Object>> regraNegocio(RegraNegocioException e, HttpServletRequest request) {
        return erro(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> naoEncontrado(RecursoNaoEncontradoException e, HttpServletRequest request) {
        return erro(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> requisicaoInvalida(Exception e, HttpServletRequest request) {
        return erro(HttpStatus.BAD_REQUEST, "Requisição inválida: verifique os dados enviados.", request);
    }

    /** Parâmetro com tipo errado, ex.: limite=abc. A mensagem informa qual parâmetro corrigir. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> parametroInvalido(MethodArgumentTypeMismatchException e,
            HttpServletRequest request) {
        String mensagem = "Requisição inválida: valor '" + e.getValue() + "' não aceito no parâmetro '"
                + e.getName() + "'.";
        return erro(HttpStatus.BAD_REQUEST, mensagem, request);
    }

    /**
     * Banco de dados fora do ar ou inacessível: 503 para que o cliente (dashboard)
     * saiba que é uma falha temporária e possa tentar novamente.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> bancoIndisponivel(DataAccessException e, HttpServletRequest request) {
        log.error("Falha de acesso ao banco de dados em {}", request.getRequestURI(), e);
        return erro(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível consultar o banco de dados no momento. Tente novamente em instantes.", request);
    }

    private ResponseEntity<Map<String, Object>> erro(HttpStatus status, String mensagem, HttpServletRequest request) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("dataHora", LocalDateTime.now().toString());
        corpo.put("status", status.value());
        corpo.put("erro", status.getReasonPhrase());
        corpo.put("mensagem", mensagem);
        corpo.put("caminho", request.getRequestURI());
        return ResponseEntity.status(status).body(corpo);
    }
}

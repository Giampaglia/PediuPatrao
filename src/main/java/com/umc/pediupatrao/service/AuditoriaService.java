package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    public static final String ENTIDADE_PEDIDO = "PEDIDO";
    public static final String ENTIDADE_CLIENTE = "CLIENTE";
    public static final String ENTIDADE_USUARIO = "USUARIO";
    public static final String ENTIDADE_SISTEMA = "SISTEMA";

    private static final int LIMITE_CONSULTA = 500;

    // Nunca gravar credenciais na auditoria, mesmo que algum serviço tente
    private static final Set<String> CAMPOS_PROIBIDOS = Set.of("password", "senha", "token", "credencial", "secret");

    private final RegistroAuditoriaRepository repository;
    private final UsuarioLogado usuarioLogado;

    public AuditoriaService(RegistroAuditoriaRepository repository, UsuarioLogado usuarioLogado) {
        this.repository = repository;
        this.usuarioLogado = usuarioLogado;
    }

    public RegistroAuditoria registrar(TipoOperacao operacao, String entidade, String entidadeId,
            List<AlteracaoCampo> alteracoes, String justificativa) {
        List<AlteracaoCampo> permitidas = alteracoes == null ? List.of()
                : alteracoes.stream().filter(a -> !campoProibido(a.getCampo())).toList();

        RegistroAuditoria registro = new RegistroAuditoria(
                null, // sempre um novo documento: registros nunca são sobrescritos
                usuarioLogado.username(),
                usuarioLogado.perfil(),
                LocalDateTime.now(),
                operacao,
                entidade,
                entidadeId,
                permitidas,
                justificativa);
        return repository.save(registro);
    }

    /** Consulta com filtros opcionais (todos podem ser nulos/vazios). */
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<RegistroAuditoria> consultar(String entidade, String entidadeId, String usuario, TipoOperacao operacao) {
        RegistroAuditoria filtro = new RegistroAuditoria(null, vazioParaNulo(usuario), null, null, operacao,
                maiusculo(vazioParaNulo(entidade)), vazioParaNulo(entidadeId), null, null);
        ExampleMatcher matcher = ExampleMatcher.matching()
                .withIgnoreNullValues()
                .withMatcher("usuario", m -> m.contains().ignoreCase());
        return repository.findAll(Example.of(filtro, matcher),
                PageRequest.of(0, LIMITE_CONSULTA, Sort.by(Sort.Direction.DESC, "dataHora"))).getContent();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<RegistroAuditoria> historicoDe(String entidade, String entidadeId) {
        return repository.findByEntidadeAndEntidadeIdOrderByDataHoraDesc(entidade, entidadeId);
    }

    private static boolean campoProibido(String campo) {
        if (campo == null) {
            return false;
        }
        String normalizado = campo.toLowerCase(Locale.ROOT);
        return CAMPOS_PROIBIDOS.stream().anyMatch(normalizado::contains);
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static String maiusculo(String valor) {
        return valor == null ? null : valor.toUpperCase(Locale.ROOT);
    }
}

package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Evento de auditoria. Os campos são finais e não há setters: depois de
 * gravado, o registro não pode ser alterado pela aplicação.
 */
@Document(collection = "auditoria")
public class RegistroAuditoria {

    @Id
    private String id;

    private final String usuario;
    private final String perfil;
    @Indexed
    private final LocalDateTime dataHora;
    private final TipoOperacao operacao;
    private final String entidade;
    private final String entidadeId;
    private final List<AlteracaoCampo> alteracoes;
    private final String justificativa;

    public RegistroAuditoria(String id, String usuario, String perfil, LocalDateTime dataHora,
            TipoOperacao operacao, String entidade, String entidadeId,
            List<AlteracaoCampo> alteracoes, String justificativa) {
        this.id = id;
        this.usuario = usuario;
        this.perfil = perfil;
        this.dataHora = dataHora;
        this.operacao = operacao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        // null é preservado para que o objeto possa servir de filtro (Query by Example)
        this.alteracoes = alteracoes == null ? null : List.copyOf(alteracoes);
        this.justificativa = justificativa;
    }

    public String getId() {
        return id;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getPerfil() {
        return perfil;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public TipoOperacao getOperacao() {
        return operacao;
    }

    public String getEntidade() {
        return entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public List<AlteracaoCampo> getAlteracoes() {
        return alteracoes == null ? List.of() : alteracoes;
    }

    public String getJustificativa() {
        return justificativa;
    }
}

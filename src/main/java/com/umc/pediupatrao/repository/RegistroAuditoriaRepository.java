package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import java.util.List;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.QueryByExampleExecutor;

/**
 * Repositório somente de inclusão e consulta. Por estender {@link Repository}
 * (e não MongoRepository/CrudRepository), não expõe métodos de exclusão.
 * O serviço de auditoria sempre grava registros novos (id nulo), então save
 * resulta em inclusão, nunca em atualização.
 */
public interface RegistroAuditoriaRepository
        extends Repository<RegistroAuditoria, String>, QueryByExampleExecutor<RegistroAuditoria> {

    RegistroAuditoria save(RegistroAuditoria registro);

    List<RegistroAuditoria> findByEntidadeAndEntidadeIdOrderByDataHoraDesc(String entidade, String entidadeId);
}

package com.roles.usermanagement.modules.assistance.repository;

import com.roles.usermanagement.modules.assistance.entity.AssistanceResponse;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistanceResponseRepository extends JpaRepository<AssistanceResponse, Long> {

    Optional<AssistanceResponse> findByQueryId(Long queryId);

    /** Respuestas de varias consultas con sus fuentes y artículos en una sola consulta (sin N+1). */
    @EntityGraph(attributePaths = {"sources", "sources.article"})
    @Query("select distinct r from AssistanceResponse r where r.query.id in :queryIds")
    List<AssistanceResponse> findWithSourcesByQueryIdIn(@Param("queryIds") Collection<Long> queryIds);
}

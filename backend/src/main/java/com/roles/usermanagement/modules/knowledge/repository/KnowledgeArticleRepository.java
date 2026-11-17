package com.roles.usermanagement.modules.knowledge.repository;

import com.roles.usermanagement.modules.knowledge.entity.KnowledgeArticle;
import com.roles.usermanagement.modules.knowledge.enums.IndexSyncStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from KnowledgeArticle a where a.id = :id")
    Optional<KnowledgeArticle> findForUpdate(@Param("id") Long id);

    /**
     * Bloqueo de lectura sobre los artículos citados por una respuesta: mientras se comprueban y se
     * registran como fuentes no pueden editarse ni desactivarse. El orden por id evita interbloqueos.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select a from KnowledgeArticle a where a.id in :ids order by a.id")
    List<KnowledgeArticle> findAllByIdForShare(@Param("ids") Collection<Long> ids);

    Page<KnowledgeArticle> findByActiveTrueAndVerifiedTrue(Pageable pageable);

    Page<KnowledgeArticle> findByAuthorUsername(String username, Pageable pageable);

    List<KnowledgeArticle> findByActiveTrueAndVerifiedTrueOrderByIdAsc();

    /** Ids de artículos por sincronizar posteriores a {@code after}, en orden; el tope va en {@code limit}. */
    @Query("select a.id from KnowledgeArticle a where a.indexStatus in :statuses and a.id > :after order by a.id")
    List<Long> findIdsToSync(@Param("statuses") Collection<IndexSyncStatus> statuses, @Param("after") long after,
                             Pageable limit);

    long countByActiveTrueAndVerifiedTrue();

    /** ¿Hay otro artículo indexado con un modelo o dimensión de embeddings distintos? */
    @Query("""
            select count(a) > 0 from KnowledgeArticle a
            where a.id <> :id and a.indexStatus = com.roles.usermanagement.modules.knowledge.enums.IndexSyncStatus.INDEXED
              and (a.embeddingModel <> :model or a.embeddingDimension <> :dimension)
            """)
    boolean existsIndexedWithDifferentEmbedding(@Param("id") Long id, @Param("model") String model,
                                                @Param("dimension") Integer dimension);
}

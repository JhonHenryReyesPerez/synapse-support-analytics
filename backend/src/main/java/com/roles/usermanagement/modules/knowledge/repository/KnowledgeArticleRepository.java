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

    Page<KnowledgeArticle> findByActiveTrueAndVerifiedTrue(Pageable pageable);

    Page<KnowledgeArticle> findByAuthorUsername(String username, Pageable pageable);

    List<KnowledgeArticle> findByActiveTrueAndVerifiedTrueOrderByIdAsc();

    List<KnowledgeArticle> findByIndexStatusInOrderByIdAsc(Collection<IndexSyncStatus> statuses);

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

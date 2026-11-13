package com.roles.usermanagement.modules.knowledge.entity;

import com.roles.usermanagement.modules.knowledge.enums.IndexSyncStatus;
import com.roles.usermanagement.persistance.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Artículo de la base de conocimiento (RF02). Solo los artículos con active=true y verified=true
 * son elegibles para alimentar el RAG. No se eliminan: las respuestas históricas los referencian.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "knowledge_articles", indexes = {
        @Index(name = "idx_article_eligibility", columnList = "active, verified"),
        @Index(name = "idx_article_author", columnList = "author_username"),
        @Index(name = "idx_article_sync", columnList = "index_status")
})
public class KnowledgeArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String problem;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String symptoms;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String cause;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String solution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_username", referencedColumnName = "username", nullable = false)
    private UserEntity author;

    @Column(nullable = false)
    private boolean verified;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_username", referencedColumnName = "username")
    private UserEntity verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(nullable = false)
    private boolean active;

    /** Se incrementa con cada cambio de contenido técnico; identifica la evidencia usada por una respuesta. */
    @Column(name = "content_version", nullable = false)
    private int contentVersion;

    /** SHA-256 del contenido técnico de la versión vigente. */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "index_status", nullable = false, length = 20)
    private IndexSyncStatus indexStatus;

    /** Versión presente en el índice vectorial; null si no hay vectores del artículo. */
    @Column(name = "indexed_version")
    private Integer indexedVersion;

    @Column(name = "indexed_at")
    private LocalDateTime indexedAt;

    @Column(name = "embedding_model", length = 120)
    private String embeddingModel;

    @Column(name = "embedding_dimension")
    private Integer embeddingDimension;

    /** Código seguro del último fallo de sincronización (sin detalles del proveedor). */
    @Column(name = "index_error_code", length = 60)
    private String indexErrorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isEligible() {
        return active && verified;
    }
}

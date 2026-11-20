package com.roles.usermanagement.modules.assistance.entity;

import com.roles.usermanagement.modules.knowledge.entity.KnowledgeArticle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FuenteRespuesta: artículo usado como evidencia de una respuesta, con la versión recuperada, su
 * similitud y una copia del contenido utilizado. La copia permite mostrar la fuente tal como era
 * aunque el artículo cambie o se desactive después.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assistance_response_sources",
        uniqueConstraints = @UniqueConstraint(name = "uk_assist_source_article", columnNames = {"response_id", "article_id"}),
        indexes = {
                @Index(name = "idx_assist_source_response", columnList = "response_id, source_order"),
                @Index(name = "idx_assist_source_article", columnList = "article_id")
        })
public class ResponseSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_id", nullable = false, updatable = false)
    private AssistanceResponse response;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false, updatable = false)
    private KnowledgeArticle article;

    /** Versión de contenido del artículo que respaldó la respuesta. */
    @Column(name = "article_version", nullable = false, updatable = false)
    private int articleVersion;

    /** Similitud coseno devuelta por la recuperación. No es una probabilidad de éxito. */
    @Column(updatable = false)
    private Double score;

    /** Posición de la fuente en la respuesta: 1 es la más similar. */
    @Column(name = "source_order", nullable = false, updatable = false)
    private int sourceOrder;

    @Column(name = "article_title", nullable = false, updatable = false, length = 200)
    private String articleTitle;

    /** Copia del contenido técnico usado como evidencia (problema, síntomas, causa y solución). */
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "content_hash", nullable = false, updatable = false, length = 64)
    private String contentHash;
}

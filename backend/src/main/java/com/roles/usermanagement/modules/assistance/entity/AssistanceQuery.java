package com.roles.usermanagement.modules.assistance.entity;

import com.roles.usermanagement.modules.assistance.enums.AssistanceStatus;
import com.roles.usermanagement.modules.ticket.entity.Ticket;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ConsultaIA (RF08): una pregunta al asistente sobre un incidente. Se persiste en PENDING antes
 * de llamar a n8n y termina en ANSWERED, NO_RESULTS o ERROR. No se elimina ni se reutiliza: un
 * reintento explícito crea otra consulta enlazada con {@link #retryOf}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assistance_queries",
        uniqueConstraints = @UniqueConstraint(name = "uk_assist_query_request", columnNames = "request_id"),
        indexes = {
                @Index(name = "idx_assist_query_ticket_created", columnList = "ticket_id, created_at, id"),
                @Index(name = "idx_assist_query_status_created", columnList = "status, created_at")
        })
public class AssistanceQuery {

    @Column(name="retrieval_profile", length=40)
    private String retrievalProfile;

    @Column(name="advanced_json", columnDefinition="TEXT")
    private String advancedJson;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private Ticket ticket;

    /** Usuario autenticado que hizo la consulta; se toma de Spring Security, nunca del cuerpo. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_username", referencedColumnName = "username", nullable = false, updatable = false)
    private UserEntity requester;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssistanceStatus status;

    /** Identificador idempotente de la consulta (UUID); correlaciona la solicitud y la respuesta de n8n. */
    @Column(name = "request_id", nullable = false, updatable = false, length = 36)
    private String requestId;

    /** Código seguro del fallo cuando status = ERROR (sin detalles del proveedor). */
    @Column(name = "error_code", length = 60)
    private String errorCode;

    /** Máximo de artículos solicitado a la recuperación. */
    @Column(name = "max_results", nullable = false, updatable = false)
    private int maxResults;

    /** Umbral de la vía vectorial; v2 híbrida también admite evidencia literal. */
    @Column(name = "min_score", nullable = false, updatable = false)
    private double minScore;

    /** Llamadas hechas a n8n: 1, o 2 si hubo reintento por fuentes obsoletas. */
    @Column(nullable = false)
    private int attempts;

    /** Consulta anterior que este reintento explícito repite; null si es una consulta original. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retry_of_id", updatable = false)
    private AssistanceQuery retryOf;

    @Column(name = "embedding_model", length = 120)
    private String embeddingModel;

    @Column(name = "similarity_metric", length = 30)
    private String similarityMetric;

    @Column(name = "workflow_version", length = 60)
    private String workflowVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Instante en que alcanzó un estado terminal; null mientras está PENDING. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}

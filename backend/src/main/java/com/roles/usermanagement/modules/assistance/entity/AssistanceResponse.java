package com.roles.usermanagement.modules.assistance.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * RespuestaIA: orientación generada para una consulta ANSWERED. Existe como máximo una por
 * consulta (restricción única) y siempre tiene al menos una fuente. No se edita ni se elimina.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assistance_responses",
        uniqueConstraints = @UniqueConstraint(name = "uk_assist_response_query", columnNames = "query_id"))
public class AssistanceResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trust_level", nullable = false)
    private Long trust_level;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "query_id", nullable = false, updatable = false)
    private AssistanceQuery query;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String answer;

    @ElementCollection
    @CollectionTable(name = "assistance_response_steps", joinColumns = @JoinColumn(name = "response_id"))
    @OrderColumn(name = "step_order")
    @Column(name = "step", nullable = false, columnDefinition = "TEXT")
    @BatchSize(size = 100)
    private List<String> recommendedSteps = new ArrayList<>();

    /** Sin cascada REMOVE: la evidencia histórica no se borra desde la respuesta. */
    @OneToMany(mappedBy = "response", cascade = CascadeType.PERSIST)
    @OrderBy("sourceOrder ASC")
    private List<ResponseSource> sources = new ArrayList<>();

    @Column(name = "generation_model", length = 120)
    private String generationModel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

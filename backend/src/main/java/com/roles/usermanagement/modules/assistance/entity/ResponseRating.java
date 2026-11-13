package com.roles.usermanagement.modules.assistance.entity;

import com.roles.usermanagement.persistance.entity.UserEntity;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Calificacion (RF08): utilidad percibida de una respuesta, de 1 a 5. Hay como máximo una por
 * respuesta y usuario; calificar de nuevo la actualiza. No modifica el artículo ni su similitud.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assistance_ratings",
        uniqueConstraints = @UniqueConstraint(name = "uk_assist_rating_response_user", columnNames = {"response_id", "username"}),
        check = @CheckConstraint(name = "ck_assist_rating_score", constraint = "score between 1 and 5"))
public class ResponseRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_id", nullable = false, updatable = false)
    private AssistanceResponse response;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "username", referencedColumnName = "username", nullable = false, updatable = false)
    private UserEntity user;

    @Column(nullable = false)
    private int score;

    @Column(name = "comment_text", length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

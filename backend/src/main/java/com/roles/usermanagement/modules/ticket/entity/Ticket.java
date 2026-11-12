package com.roles.usermanagement.modules.ticket.entity;
import com.roles.usermanagement.modules.lifecycle.enums.TicketStatus;
import com.roles.usermanagement.modules.category.entity.Category;
import com.roles.usermanagement.modules.ticket.enums.TicketPriority;
import com.roles.usermanagement.persistance.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_ticket_status", columnList = "status"),
        @Index(name = "idx_ticket_applicant", columnList = "applicant_username"),
        @Index(name = "idx_ticket_technician", columnList = "technician_username"),
        @Index(name = "idx_ticket_category", columnList = "category_id"),
        @Index(name = "idx_ticket_priority", columnList = "priority"),
        @Index(name = "idx_ticket_created", columnList = "created_at, id")
})
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status;

    /**
     * User who created the issue (Applicant/Requester).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_username", referencedColumnName = "username", nullable = false)
    private UserEntity applicant;

    /**
     * Technician assigned to handle the issue.
     * Can be null while ticket remains in OPEN status.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_username", referencedColumnName = "username")
    private UserEntity technician;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Auditoría de edición; NO es la fecha de resolución. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Instante de la transición a RESOLVED (B08). Null si aún no se resuelve o si es un dato previo incompleto. */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** Instante de la confirmación de cierre; no reemplaza a resolvedAt. */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** Nota de solución registrada por el técnico al resolver. */
    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = TicketStatus.OPEN;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

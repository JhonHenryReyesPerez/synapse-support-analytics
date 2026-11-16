package com.roles.usermanagement.modules.assignment.service;

import com.roles.usermanagement.modules.assignment.dto.TicketAssignmentResponse;
import com.roles.usermanagement.modules.assignment.entity.TicketAssignmentLog;
import com.roles.usermanagement.modules.assignment.enums.AssignmentType;
import com.roles.usermanagement.modules.assignment.repository.TicketAssignmentLogRepository;
import com.roles.usermanagement.modules.lifecycle.enums.TicketStatus;
import com.roles.usermanagement.modules.statushistory.entity.StatusHistory;
import com.roles.usermanagement.modules.statushistory.enums.StatusHistoryAction;
import com.roles.usermanagement.modules.technician.entity.TechnicianProfile;
import com.roles.usermanagement.modules.technician.enums.TechnicianAvailabilityStatus;
import com.roles.usermanagement.modules.technician.repository.TechnicianRepository;
import com.roles.usermanagement.modules.ticket.entity.Ticket;
import com.roles.usermanagement.persistance.entity.UserEntity;
import com.roles.usermanagement.persistance.entity.UserRoleEntity;
import com.roles.usermanagement.persistance.crud.UserCrudRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Único caso de uso de asignación y reasignación (B05). Lo utilizan tanto los endpoints
 * /assign y /reassign como las acciones ASSIGN/REASSIGN de /transitions y /status, de modo
 * que todas las rutas aplican las mismas validaciones y dejan la misma auditoría.
 */
@Service
public class TicketAssignmentService {

    private final TicketAssignmentLogRepository assignmentLogs;
    private final UserCrudRepository users;
    private final TechnicianRepository technicianProfiles;

    @PersistenceContext
    private EntityManager entityManager;

    public TicketAssignmentService(TicketAssignmentLogRepository assignmentLogs, UserCrudRepository users,
                                   TechnicianRepository technicianProfiles) {
        this.assignmentLogs = assignmentLogs;
        this.users = users;
        this.technicianProfiles = technicianProfiles;
    }

    /**
     * Assigns an open ticket for the first time and records the status change.
     */
    @Transactional
    public TicketAssignmentResponse assign(Long ticketId, String technicianUsername, String adminUsername) {
        UserEntity admin = findUser(adminUsername, "El administrador autenticado no existe");
        requireRole(admin, "ADMIN", "Solo un ADMIN puede asignar incidencias");

        Ticket ticket = findTicket(ticketId);
        if (ticket.getStatus() != TicketStatus.OPEN) {
            throw conflict("Solo se pueden asignar incidencias en estado ABIERTA");
        }

        UserEntity technician = requireAssignableTechnician(technicianUsername);

        ticket.setTechnician(technician);
        ticket.setStatus(TicketStatus.ASSIGNED);

        TicketAssignmentLog log = TicketAssignmentLog.builder()
                .ticket(ticket)
                .newTechnician(technician)
                .assignedBy(admin)
                .assignmentType(AssignmentType.INITIAL)
                .build();
        TicketAssignmentLog saved = assignmentLogs.save(log);
        recordStatusHistory(ticket, admin, TicketStatus.OPEN, ticket.getStatus());

        return toResponse(saved, ticket);
    }

    /**
     * Atomically reassigns an assigned ticket to a different technician.
     */
    @Transactional
    public TicketAssignmentResponse reassign(
            Long ticketId,
            String technicianUsername,
            String justification,
            String adminUsername
    ) {
        UserEntity admin = findUser(adminUsername, "El administrador autenticado no existe");
        requireRole(admin, "ADMIN", "Solo un ADMIN puede reasignar incidencias");

        Ticket ticket = findTicket(ticketId);
        if (ticket.getStatus() != TicketStatus.ASSIGNED) {
            throw conflict("Solo se pueden reasignar incidencias en estado ASIGNADA");
        }
        if (justification == null || justification.isBlank()) {
            throw badRequest("La justificación de la reasignación es obligatoria");
        }
        if (justification.trim().length() > 300) {
            throw badRequest("La justificación no puede superar 300 caracteres");
        }
        if (ticket.getTechnician() == null) {
            throw conflict("La incidencia no tiene un técnico asignado");
        }
        if (technicianUsername != null
                && Objects.equals(ticket.getTechnician().getUsername(), technicianUsername.trim())) {
            throw conflict("La incidencia ya está asignada a ese técnico");
        }

        UserEntity technician = requireAssignableTechnician(technicianUsername);

        UserEntity previousTechnician = ticket.getTechnician();
        ticket.setTechnician(technician);

        TicketAssignmentLog log = TicketAssignmentLog.builder()
                .ticket(ticket)
                .previousTechnician(previousTechnician)
                .newTechnician(technician)
                .assignedBy(admin)
                .assignmentType(AssignmentType.REASSIGNMENT)
                .justification(justification.trim())
                .build();
        TicketAssignmentLog saved = assignmentLogs.save(log);
        recordStatusHistory(ticket, admin, TicketStatus.ASSIGNED, ticket.getStatus());

        return toResponse(saved, ticket);
    }

    private Ticket findTicket(Long ticketId) {
        Ticket ticket = entityManager.find(Ticket.class, ticketId, LockModeType.PESSIMISTIC_WRITE);
        if (ticket == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La incidencia no existe");
        }
        return ticket;
    }

    /**
     * Valida al técnico destino: cuenta existente y activa, rol TECHNICIAN vigente y perfil
     * disponible. El rol es la fuente de verdad; el perfil se crea si falta y nunca lo sustituye.
     */
    private UserEntity requireAssignableTechnician(String technicianUsername) {
        if (technicianUsername == null || technicianUsername.isBlank()) {
            throw badRequest("El usuario del técnico es obligatorio");
        }
        // Bloqueo compartido: un cambio de rol o una baja simultánea del técnico espera a que termine la
        // asignación, y dos reasignaciones cruzadas no se bloquean entre sí (uno exclusivo las interbloquearía).
        UserEntity technician = entityManager.find(UserEntity.class, technicianUsername.trim(), LockModeType.PESSIMISTIC_READ);
        if (technician == null) {
            throw badRequest("El técnico seleccionado no existe");
        }
        boolean isTechnician = technician.getRoles() != null && technician.getRoles().stream()
                .map(UserRoleEntity::getRole)
                .anyMatch("TECHNICIAN"::equals);
        if (!isTechnician) {
            throw badRequest("El usuario seleccionado no tiene el rol TECHNICIAN");
        }
        if (Boolean.TRUE.equals(technician.getDisabled()) || Boolean.TRUE.equals(technician.getLocked())) {
            throw badRequest("El técnico seleccionado no tiene la cuenta activa");
        }
        TechnicianProfile profile = technicianProfiles.findById(technician.getUsername())
                .orElseGet(() -> technicianProfiles.save(TechnicianProfile.builder()
                        .user(technician)
                        .availabilityStatus(TechnicianAvailabilityStatus.AVAILABLE)
                        .activeTicketsCount(0)
                        .build()));
        if (profile.getAvailabilityStatus() != TechnicianAvailabilityStatus.AVAILABLE) {
            throw conflict("El técnico seleccionado no está disponible");
        }
        profile.setLastAssignedAt(LocalDateTime.now());
        return technician;
    }

    private UserEntity findUser(String username, String message) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No se pudo identificar al usuario autenticado");
        }
        return users.findById(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, message));
    }

    private void requireRole(UserEntity user, String role, String message) {
        boolean hasRole = user.getRoles() != null && user.getRoles().stream()
                .map(UserRoleEntity::getRole)
                .anyMatch(role::equals);
        if (!hasRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private void recordStatusHistory(Ticket ticket, UserEntity admin, TicketStatus previous, TicketStatus current) {
        StatusHistory history = StatusHistory.builder()
                .ticket(ticket)
                .previousStatus(previous)
                .newStatus(current)
                .changedBy(admin)
                .action(StatusHistoryAction.ASSIGNMENT)
                .build();
        entityManager.persist(history);
    }

    private TicketAssignmentResponse toResponse(TicketAssignmentLog log, Ticket ticket) {
        return new TicketAssignmentResponse(
                log.getId(),
                ticket.getId(),
                log.getPreviousTechnician() == null ? null : log.getPreviousTechnician().getUsername(),
                log.getNewTechnician().getUsername(),
                log.getAssignedBy().getUsername(),
                log.getAssignmentType(),
                log.getJustification(),
                log.getAssignedAt(),
                ticket.getStatus()
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

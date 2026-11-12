package com.roles.usermanagement.modules.lifecycle.service;

import com.roles.usermanagement.modules.lifecycle.dto.StateTransitionRequest;
import com.roles.usermanagement.modules.lifecycle.dto.StateTransitionResponse;
import com.roles.usermanagement.modules.lifecycle.entity.LifecycleRule;
import com.roles.usermanagement.modules.lifecycle.enums.TicketStatus;
import com.roles.usermanagement.modules.lifecycle.enums.TransitionAction;
import com.roles.usermanagement.modules.lifecycle.repository.LifecycleRuleRepository;
import com.roles.usermanagement.modules.statushistory.entity.StatusHistory;
import com.roles.usermanagement.modules.statushistory.enums.StatusHistoryAction;
import com.roles.usermanagement.modules.statushistory.service.StatusHistoryService;
import com.roles.usermanagement.modules.assignment.dto.TicketAssignmentResponse;
import com.roles.usermanagement.modules.assignment.service.TicketAssignmentService;
import com.roles.usermanagement.modules.ticket.entity.Ticket;
import com.roles.usermanagement.modules.ticket.repository.TicketRepository;
import com.roles.usermanagement.modules.ticket.service.TicketAccessPolicy;
import com.roles.usermanagement.persistance.crud.UserCrudRepository;
import com.roles.usermanagement.persistance.entity.UserEntity;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketLifecycleService {

    public static final int MAX_RESOLUTION_NOTE = 5000;

    private final TicketRepository ticketRepository;
    private final LifecycleRuleRepository ruleRepository;
    private final TicketAssignmentService assignmentService;
    private final UserCrudRepository userRepository;
    private final StatusHistoryService historyService;
    private final TicketAccessPolicy policy;

    // Constructor injection: Spring provides each dependency when creating the service
    public TicketLifecycleService(TicketRepository ticketRepository,
                                  LifecycleRuleRepository ruleRepository,
                                  TicketAssignmentService assignmentService,
                                  UserCrudRepository userRepository,
                                  StatusHistoryService historyService,
                                  TicketAccessPolicy policy) {
        this.ticketRepository = ticketRepository;
        this.ruleRepository = ruleRepository;
        this.assignmentService = assignmentService;
        this.userRepository = userRepository;
        this.historyService = historyService;
        this.policy = policy;
    }

    // Single transaction: status change and history record are committed together or not at all (RF-06)
    @Transactional
    public StateTransitionResponse transition(Long ticketId, StateTransitionRequest request, Authentication auth) {

        if (request.action() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acción o estado requerido no válido");
        }

        // B05: asignar y reasignar tienen una única implementación, compartida con /assign y /reassign.
        if (request.action() == TransitionAction.ASSIGN || request.action() == TransitionAction.REASSIGN) {
            return delegateAssignment(ticketId, request, auth);
        }

        // 1. Find ticket with LOCKING; if it does not exist, 404
        Ticket ticket = ticketRepository.findForUpdate(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        // Users without permission to view ticket must not know its state (not even via 409)
        policy.assertCanView(ticket, auth);

        TicketStatus previousStatus = ticket.getStatus();

        if (request.action() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acción o estado requerido no válido");
        }

        // 2. Find rule; if not found, transition is not permitted -> 409
        LifecycleRule rule = ruleRepository
                .findByCurrentStatusAndAction(previousStatus, request.action())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Action " + request.action() + " is not allowed when the ticket is " + previousStatus));

        // 3. Verify requester satisfies the rule requirements
        assertCanPerform(rule, ticket, auth);

        // 5. Change status and record history (same transaction)
        UserEntity actor = userRepository.findById(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // B08: resolver exige nota de solución y fija resolvedAt; cerrar fija closedAt sin tocar resolvedAt.
        LocalDateTime now = LocalDateTime.now();
        if (request.action() == TransitionAction.RESOLVE) {
            String note = request.resolutionNote() == null ? "" : request.resolutionNote().trim();
            if (note.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nota de solución es obligatoria para resolver");
            }
            if (note.length() > MAX_RESOLUTION_NOTE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La nota de solución no puede superar " + MAX_RESOLUTION_NOTE + " caracteres");
            }
            ticket.setResolutionNote(note);
            ticket.setResolvedAt(now);
        } else if (request.action() == TransitionAction.CONFIRM_CLOSE) {
            ticket.setClosedAt(now);
        }

        ticket.setStatus(rule.getTargetStatus());
        ticketRepository.save(ticket);

        StatusHistory entry = historyService.record(
                ticket, previousStatus, rule.getTargetStatus(), actor, toHistoryAction(request.action()));

        // 6. Build response (without exposing entities)
        return new StateTransitionResponse(
                ticket.getId(),
                request.action(),
                previousStatus,
                ticket.getStatus(),
                ticket.getTechnician() == null ? null : ticket.getTechnician().getUsername(),
                actor.getUsername(),
                entry.getChangedAt());
    }

    private StateTransitionResponse delegateAssignment(Long ticketId, StateTransitionRequest request, Authentication auth) {
        boolean initial = request.action() == TransitionAction.ASSIGN;
        TicketAssignmentResponse result = initial
                ? assignmentService.assign(ticketId, request.technicianUsername(), auth.getName())
                : assignmentService.reassign(ticketId, request.technicianUsername(), request.justification(), auth.getName());
        return new StateTransitionResponse(
                result.ticketId(),
                request.action(),
                initial ? TicketStatus.OPEN : TicketStatus.ASSIGNED,
                result.status(),
                result.newTechnicianUsername(),
                result.assignedByUsername(),
                result.assignedAt());
    }

    // Verifies requester satisfies rule requirements; otherwise throws 403
    private void assertCanPerform(LifecycleRule rule, Ticket ticket, Authentication auth) {
        // User must have the role required by rule
        if (!policy.hasRole(auth, rule.getRequiredRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your role cannot perform this action");
        }
        // If rule requires applicant, user must be the ticket creator
        if (rule.isMustBeApplicant() && !policy.isApplicant(ticket, auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the applicant can perform this action");
        }
        // If rule requires assigned technician, user must be responsible for this ticket
        if (rule.isMustBeAssignedTechnician() && !policy.isAssignedTechnician(ticket, auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned technician can perform this action");
        }
    }

    // Translates lifecycle action into event type saved in status history
    private StatusHistoryAction toHistoryAction(TransitionAction action) {
        return switch (action) {
            case ASSIGN, REASSIGN -> StatusHistoryAction.ASSIGNMENT;
            case CONFIRM_CLOSE -> StatusHistoryAction.CLOSURE;
            case START_PROGRESS, RESOLVE -> StatusHistoryAction.STATUS_TRANSITION;
        };
    }
}
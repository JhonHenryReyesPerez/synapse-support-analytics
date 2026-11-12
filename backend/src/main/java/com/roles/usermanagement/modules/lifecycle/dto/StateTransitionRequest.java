package com.roles.usermanagement.modules.lifecycle.dto;

import com.roles.usermanagement.modules.lifecycle.enums.TransitionAction;
import jakarta.validation.constraints.NotNull;

/**
 * Ticket state transition request.
 * The client specifies WHAT action to execute; target status is determined by the lifecycle rule.
 */
public record StateTransitionRequest(
        TransitionAction action,
        String technicianUsername,
        String status,
        /** Obligatoria para REASSIGN. */
        String justification,
        /** Nota de solución; obligatoria para RESOLVE. */
        String resolutionNote
) {
    public StateTransitionRequest {
        if (action == null && status != null && !status.isBlank()) {
            String s = status.trim().toUpperCase();
            action = switch (s) {
                case "IN_PROGRESS", "EN_PROCESO" -> TransitionAction.START_PROGRESS;
                case "RESOLVED", "RESUELTA" -> TransitionAction.RESOLVE;
                case "CLOSED", "CERRADA" -> TransitionAction.CONFIRM_CLOSE;
                case "ASSIGNED", "ASIGNADA" -> TransitionAction.ASSIGN;
                default -> null;
            };
        }
    }
}
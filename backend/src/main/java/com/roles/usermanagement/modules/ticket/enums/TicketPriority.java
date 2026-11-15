package com.roles.usermanagement.modules.ticket.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

/**
 * Prioridad canónica: una sola representación por valor en base de datos y respuestas.
 * Los nombres en inglés se aceptan únicamente como alias de entrada.
 */
public enum TicketPriority {
    BAJA,
    MEDIA,
    ALTA,
    CRITICA;

    @JsonCreator
    public static TicketPriority fromString(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "BAJA", "LOW" -> BAJA;
            case "MEDIA", "MEDIUM" -> MEDIA;
            case "ALTA", "HIGH" -> ALTA;
            case "CRITICA", "CRÍTICA", "CRITICAL" -> CRITICA;
            default -> throw new IllegalArgumentException("Prioridad no válida: " + value);
        };
    }
}

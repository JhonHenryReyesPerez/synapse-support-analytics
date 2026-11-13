package com.roles.usermanagement.modules.assistance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** Calificación registrada para la respuesta de una consulta. */
public record RatingResponse(
        Long queryId,
        @Schema(description = "Utilidad percibida, de 1 a 5")
        int score,
        String comment,
        String ratedBy,
        LocalDateTime createdAt,
        @Schema(description = "Cambia cuando el usuario vuelve a calificar la misma respuesta")
        LocalDateTime updatedAt
) {
}

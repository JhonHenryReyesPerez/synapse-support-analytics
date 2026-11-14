package com.roles.usermanagement.modules.assistance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Calificación de una respuesta. La puntuación se recibe como número para poder rechazar los
 * decimales (4.5) en lugar de truncarlos en silencio.
 */
public record RatingRequest(

        @Schema(description = "Utilidad de la respuesta: entero de 1 (nada útil) a 5 (muy útil)",
                type = "integer", minimum = "1", maximum = "5", example = "4")
        @NotNull(message = "La puntuación es obligatoria")
        @DecimalMin(value = "1", message = "La puntuación debe estar entre 1 y 5")
        @DecimalMax(value = "5", message = "La puntuación debe estar entre 1 y 5")
        @Digits(integer = 1, fraction = 0, message = "La puntuación debe ser un número entero entre 1 y 5")
        BigDecimal score,

        @Schema(description = "Comentario opcional (máximo 1000 caracteres)")
        @Size(max = 1000, message = "El comentario no puede superar 1000 caracteres")
        String comment
) {
}

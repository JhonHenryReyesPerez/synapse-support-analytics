package com.roles.usermanagement.modules.assistance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de asistencia sobre un incidente. No incluye usuario, estado ni fuentes: el actor se
 * toma del token y el resto lo decide el servidor.
 */
public record AssistanceRequest(

        @Schema(description = "Pregunta para el asistente (máximo 2000 caracteres). Si se omite, se consulta con la "
                + "descripción del incidente. Si se envía, no puede estar vacía.")
        @Pattern(regexp = "(?s).*\\S.*", message = "La pregunta no puede estar vacía")
        @Size(max = 4000, message = "La pregunta no puede superar 2000 caracteres")
        String question,

        @Schema(description = "Identificador idempotente opcional (UUID) generado por el cliente. Repetir la solicitud "
                + "con el mismo valor devuelve la consulta ya creada en lugar de crear otra.",
                example = "3f2b8c1e-6a4d-4b7e-9c0a-5d1e2f3a4b5c")
        @Size(max = 36, message = "requestId debe ser un UUID")
        String requestId,

        @Schema(description = "Consulta anterior del mismo incidente que este reintento repite. Crea una consulta nueva "
                + "y conserva intacta la anterior.")
        @Positive(message = "retryOfQueryId debe ser positivo")
        Long retryOfQueryId
) {
}

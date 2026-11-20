package com.roles.usermanagement.modules.assistance.dto;

import com.roles.usermanagement.modules.assistance.enums.AssistanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** Consulta al asistente con su resultado: respuesta y fuentes si fue respondida, o el motivo si no. */
public record AssistanceQueryResponse(
        Long id,
        String requestId,
        Long ticketId,
        String requesterUsername,
        String question,
        AssistanceStatus status,
        @Schema(description = "Etiqueta del estado: Pendiente, Respondida, Sin resultados o Error")
        String statusLabel,
        @Schema(description = "Mensaje para el usuario según el estado. En NO_RESULTS informa la ausencia de información; "
                + "nunca contiene una solución sin respaldo.")
        String message,
        @Schema(description = "Código seguro del fallo cuando status = ERROR")
        String errorCode,
        @Schema(description = "Solución sugerida. Solo en ANSWERED")
        String answer,
        @Schema(description = "Pasos recomendados. Vacío si la consulta no fue respondida")
        List<String> recommendedSteps,
        @Schema(description = "Artículos que respaldan la respuesta, del más al menos similar")
        List<Source> sources,
        @Schema(description = "true cuando la respuesta se apoya en menos de tres artículos: evidencia limitada")
        boolean limitedEvidence,
        Retrieval retrieval,
        String generationModel,
        String workflowVersion,
        @Schema(description = "Llamadas hechas al flujo: 2 si hubo un reintento por fuentes que cambiaron")
        int attempts,
        @Schema(description = "Consulta anterior que este reintento repite")
        Long retryOfQueryId,
        @Schema(description = "Calificación dada por quien hizo la consulta; null si aún no la califica")
        RatingResponse rating,
        LocalDateTime createdAt,
        @Schema(description = "Instante del estado terminal; null mientras está pendiente")
        LocalDateTime completedAt,
        String advancedJson
) {

    public record Source(
            Long articleId,
            @Schema(description = "Título del artículo en la versión utilizada")
            String title,
            @Schema(description = "Versión de contenido que respaldó la respuesta")
            int articleVersion,
            @Schema(types={"number","null"}, format="double", description = "Similitud coseno; null si no se utilizó la vía vectorial. No es una probabilidad de éxito "
                    + "ni un porcentaje de certeza")
            Double similarity,
            @Schema(description = "Posición de recuperación: v1 por coseno, v2 por ranking")
            int order,
            @Schema(description = "Contenido del artículo tal como se usó para responder")
            String evidence,
            @Schema(description = "false si el artículo cambió o dejó de estar publicado después: la fuente es histórica")
            boolean current
    ) {
    }

    public record Retrieval(
            @Schema(description = "Máximo de artículos solicitado")
            int maxResults,
            @Schema(description = "Umbral de la vía vectorial; no se aplica al score léxico ni al ranking RRF")
            double minScore,
            @Schema(description = "Métrica de similitud; el contrato v1 usa coseno")
            String similarityMetric,
            String embeddingModel
    ) {
    }
}

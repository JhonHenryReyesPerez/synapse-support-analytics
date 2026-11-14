package com.roles.usermanagement.modules.dashboard.dto;

import com.roles.usermanagement.modules.dashboard.enums.TrendGranularity;
import com.roles.usermanagement.modules.ticket.enums.TicketPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Los siete indicadores de RF06/RF07 calculados sobre una misma cohorte: tickets creados en el
 * rango indicado que cumplen los filtros y el alcance del usuario. Los valores no se redondean;
 * el redondeo es de presentación (los porcentajes redondeados pueden no sumar 100).
 */
public record AnalyticsSummaryResponse(
        Cohort cohort,
        @Schema(description = "Indicador 1: total de incidentes de la cohorte")
        long totalIncidents,
        @Schema(description = "Indicador 2: incidentes por categoría (conteo y porcentaje del total)")
        List<CategoryCount> byCategory,
        @Schema(description = "Indicador 3: incidentes creados por día o semana, con períodos vacíos en cero")
        Trend trend,
        @Schema(description = "Indicador 4: tasa de resolución (RESOLVED o CLOSED sobre el total)")
        Resolution resolution,
        @Schema(description = "Indicador 5: tiempo promedio de resolución, total y por categoría")
        ResolutionTime resolutionTime,
        @Schema(description = "Indicador 6: categoría más recurrente; varias si hay empate; vacío si no hay incidentes")
        List<CategoryCount> mostRecurrentCategories,
        @Schema(description = "Indicador 7: incidentes por prioridad canónica (siempre las cuatro)")
        List<PriorityCount> byPriority
) {

    public record Cohort(
            @Schema(description = "GLOBAL (ADMIN), OWN (USER: sus incidentes) o ASSIGNED (TECHNICIAN: sus asignados)")
            String scope,
            @Schema(description = "Inicio inclusivo sobre createdAt; null = sin límite")
            LocalDate from,
            @Schema(description = "Último día incluido sobre createdAt; null = sin límite")
            LocalDate to,
            Long categoryId,
            TicketPriority priority,
            @Schema(description = "Zona usada para días y semanas (la semana comienza el lunes)")
            String timeZone,
            @Schema(description = "Momento del cálculo: los valores reflejan el estado actual, sin caché")
            LocalDateTime generatedAt
    ) {
    }

    public record CategoryCount(Long categoryId, String category, long count, double percentage) {
    }

    public record PriorityCount(TicketPriority priority, long count, double percentage) {
    }

    public record Trend(TrendGranularity granularity, List<TrendPoint> points) {
    }

    public record TrendPoint(
            @Schema(description = "Día, o lunes que inicia la semana")
            LocalDate periodStart,
            long count
    ) {
    }

    public record Resolution(
            @Schema(description = "Tickets en estado RESOLVED o CLOSED")
            long resolvedCount,
            @Schema(description = "100 × resolvedCount / totalIncidents; 0 si no hay incidentes")
            double ratePercentage
    ) {
    }

    public record ResolutionTime(
            @Schema(description = "Unidad de los promedios: horas transcurridas (no laborables)")
            String unit,
            @Schema(description = "AVG(resolvedAt − createdAt); null si no hay resoluciones con fecha válida")
            Double averageHours,
            @Schema(description = "Tickets resueltos con fecha válida incluidos en el promedio")
            long sampleSize,
            @Schema(description = "Tickets resueltos excluidos por no tener resolvedAt o ser anterior a createdAt")
            long excludedCount,
            List<CategoryResolutionTime> byCategory
    ) {
    }

    public record CategoryResolutionTime(Long categoryId, String category, double averageHours, long sampleSize) {
    }
}

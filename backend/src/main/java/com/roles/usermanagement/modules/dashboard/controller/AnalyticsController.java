package com.roles.usermanagement.modules.dashboard.controller;

import com.roles.usermanagement.modules.dashboard.dto.AnalyticsSummaryResponse;
import com.roles.usermanagement.modules.dashboard.enums.TrendGranularity;
import com.roles.usermanagement.modules.dashboard.service.AnalyticsService;
import com.roles.usermanagement.modules.ticket.enums.TicketPriority;
import com.roles.usermanagement.web.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analítica", description = "Indicadores de incidentes calculados desde la base relacional (RF06, RF07)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Los siete indicadores con filtros de fecha, categoría y prioridad",
            description = "Alcance según el rol: ADMIN global, USER sus incidentes, TECHNICIAN sus asignados. "
                    + "Todos los indicadores comparten la misma cohorte (tickets creados en el rango). "
                    + "Resuelto incluye RESOLVED y CLOSED. Valores sin redondear y sin caché.")
    public AnalyticsSummaryResponse summary(
            @Parameter(description = "Primer día incluido (createdAt), formato YYYY-MM-DD")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Último día incluido (createdAt), formato YYYY-MM-DD")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "BAJA, MEDIA, ALTA o CRITICA")
            @RequestParam(required = false) TicketPriority priority,
            @Parameter(description = "Agrupación de la tendencia: DAY o WEEK (por defecto WEEK, inicia el lunes)")
            @RequestParam(required = false) TrendGranularity granularity,
            Authentication auth) {
        return analyticsService.summary(from, to, categoryId, priority, granularity, auth);
    }
}

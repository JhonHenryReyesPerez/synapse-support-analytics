package com.roles.usermanagement.modules.assistance.controller;

import com.roles.usermanagement.modules.assistance.dto.AssistanceQueryResponse;
import com.roles.usermanagement.modules.assistance.dto.AssistanceRequest;
import com.roles.usermanagement.modules.assistance.service.AssistanceHistoryService;
import com.roles.usermanagement.modules.assistance.service.AssistanceService;
import com.roles.usermanagement.web.config.OpenApiConfig;
import com.roles.usermanagement.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asistente RAG (RF04, RF05, RF08). El frontend solo habla con estos endpoints: nunca con n8n,
 * Qdrant ni el proveedor de modelos.
 */
@RestController
@Tag(name = "Asistente IA", description = "Consultas al asistente sobre un incidente, con respuesta respaldada por artículos verificados")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AssistanceController {

    private final AssistanceService assistance;
    private final AssistanceHistoryService history;

    public AssistanceController(AssistanceService assistance, AssistanceHistoryService history) {
        this.assistance = assistance;
        this.history = history;
    }

    @PostMapping("/api/tickets/{ticketId}/assistance")
    @Operation(summary = "Solicitar asistencia para un incidente",
            description = "USER: sus incidentes. TECHNICIAN: los asignados. ADMIN: cualquiera. No admite incidentes cerrados. "
                    + "La consulta se guarda antes de procesarse y la respuesta llega en la misma llamada. "
                    + "status=ANSWERED incluye solución, pasos y fuentes con su similitud; status=NO_RESULTS indica que "
                    + "ningún artículo verificado fue suficientemente relevante y no contiene solución.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Consulta creada con resultado ANSWERED o NO_RESULTS"),
                    @ApiResponse(responseCode = "200", description = "Repetición con un requestId ya usado: se devuelve la consulta existente"),
                    @ApiResponse(responseCode = "400", description = "Pregunta vacía o demasiado larga; el flujo inteligente no se invoca"),
                    @ApiResponse(responseCode = "403", description = "Sin acceso al incidente"),
                    @ApiResponse(responseCode = "404", description = "El incidente no existe"),
                    @ApiResponse(responseCode = "409", description = "Incidente cerrado o consulta propia aún en proceso"),
                    @ApiResponse(responseCode = "502", description = "El flujo respondió con un error o una respuesta inválida; el cuerpo trae queryId y code"),
                    @ApiResponse(responseCode = "503", description = "El flujo no está disponible; el cuerpo trae queryId y code"),
                    @ApiResponse(responseCode = "504", description = "El flujo no respondió a tiempo; el cuerpo trae queryId y code")
            })
    public ResponseEntity<AssistanceQueryResponse> ask(@PathVariable Long ticketId,
                                                       @Valid @RequestBody(required = false) AssistanceRequest request,
                                                       Authentication auth) {
        AssistanceService.Result result = assistance.ask(ticketId, request, auth);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.query());
    }

    @GetMapping("/api/tickets/{ticketId}/assistance")
    @Operation(summary = "Historial de asistencias de un incidente",
            description = "Mismo acceso que el incidente; también disponible cuando está cerrado. "
                    + "Orden: más recientes primero. page desde 0; size 20 por defecto y máximo 100.")
    public PageResponse<AssistanceQueryResponse> list(@PathVariable Long ticketId,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size,
                                                      Authentication auth) {
        return history.listByTicket(ticketId, page, size, auth);
    }

    @GetMapping("/api/assistance/queries/{queryId}")
    @Operation(summary = "Consultar una asistencia y su resultado",
            description = "Sirve para recuperar el resultado de una consulta tras un error de red o mientras está pendiente.")
    public AssistanceQueryResponse getById(@PathVariable Long queryId, Authentication auth) {
        return history.getById(queryId, auth);
    }
}

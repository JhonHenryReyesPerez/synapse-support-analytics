package com.roles.usermanagement.modules.assistance.controller;

import com.roles.usermanagement.modules.assistance.dto.AiQueryDto;
import com.roles.usermanagement.modules.assistance.dto.AskAssistanceRequest;
import com.roles.usermanagement.modules.assistance.dto.RateResponseRequest;
import com.roles.usermanagement.modules.assistance.dto.RatingDto;
import com.roles.usermanagement.modules.assistance.service.AssistanceService;
import com.roles.usermanagement.modules.assistance.service.RatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Asistencia RAG y Calificaciones", description = "Consultas inteligentes al asistente basadas en la base de conocimiento y retroalimentación")
@SecurityRequirement(name = "Bearer Authentication")
public class AssistanceController {

    private final AssistanceService assistanceService;
    private final RatingService ratingService;

    @PostMapping("/tickets/{ticketId}/assistance")
    @Operation(summary = "Solicitar asistencia inteligente RAG para un incidente")
    public ResponseEntity<AiQueryDto> askAssistance(
            @PathVariable Long ticketId,
            @RequestBody(required = false) @Valid AskAssistanceRequest request,
            Authentication auth
    ) {
        AiQueryDto dto = assistanceService.askAssistance(ticketId, request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/assistance/queries/{queryId}")
    @Operation(summary = "Obtener el detalle y resultado de una consulta de asistencia")
    public ResponseEntity<AiQueryDto> getQuery(
            @PathVariable Long queryId,
            Authentication auth
    ) {
        return ResponseEntity.ok(assistanceService.getQuery(queryId, auth.getName()));
    }

    @PostMapping("/assistance/responses/{responseId}/rate")
    @Operation(summary = "Calificar la utilidad de una respuesta generada por IA (1 a 5 estrellas)")
    public ResponseEntity<RatingDto> rateResponse(
            @PathVariable Long responseId,
            @Valid @RequestBody RateResponseRequest request,
            Authentication auth
    ) {
        return ResponseEntity.ok(ratingService.rateResponse(responseId, request, auth.getName()));
    }

    @GetMapping("/assistance/responses/{responseId}/rating")
    @Operation(summary = "Obtener la calificación asignada a una respuesta de IA")
    public ResponseEntity<RatingDto> getRating(
            @PathVariable Long responseId
    ) {
        return ResponseEntity.ok(ratingService.getRating(responseId));
    }
}

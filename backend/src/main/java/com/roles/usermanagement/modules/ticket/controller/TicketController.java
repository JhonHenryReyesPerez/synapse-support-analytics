package com.roles.usermanagement.modules.ticket.controller;

import com.roles.usermanagement.modules.lifecycle.enums.TicketStatus;
import com.roles.usermanagement.modules.ticket.dto.TicketCreateRequest;
import com.roles.usermanagement.modules.ticket.dto.TicketResponse;
import com.roles.usermanagement.modules.ticket.dto.TicketUpdateRequest;
import com.roles.usermanagement.modules.ticket.enums.TicketPriority;
import com.roles.usermanagement.modules.ticket.service.TicketService;
import com.roles.usermanagement.web.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import com.roles.usermanagement.web.dto.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for managing tickets.
 * Only handles HTTP requests and responses; business rules reside in TicketService.
 */
@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Tickets", description = "Gestión de incidencias tecnológicas")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class TicketController {

    private final TicketService ticketService;

    // Spring injects service via constructor
    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** Creates a ticket. USER role only; initial status is OPEN. */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Create ticket")
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketCreateRequest request,
                                                 Authentication auth) {
        // 201 Created: new resource created
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.create(request, auth));
    }

    /** Lists tickets visible to the user, with optional filters. */
    @GetMapping
    @Operation(summary = "Listar tickets visibles con filtros y paginación",
            description = "page desde 0; size 20 por defecto y máximo 100. Orden: más recientes primero (createdAt, id).")
    public PageResponse<TicketResponse> list(@RequestParam(required = false) TicketStatus status,
                                     @RequestParam(required = false) TicketPriority priority,
                                     @RequestParam(required = false) Long categoryId,
                                     @RequestParam(required = false) String category,
                                     @RequestParam(required = false) Integer page,
                                     @RequestParam(required = false) Integer size,
                                     Authentication auth) {
        return ticketService.list(status, priority, categoryId, category, page, size, auth);
    }

    /** Returns ticket details if user has permission to view it. */
    @GetMapping("/{id}")
    @Operation(summary = "Get ticket by id")
    public TicketResponse getById(@PathVariable Long id, Authentication auth) {
        return ticketService.getById(id, auth);
    }

    /** Updates a ticket. Requester only while OPEN and unassigned. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Update ticket")
    public TicketResponse update(@PathVariable Long id,
                                 @Valid @RequestBody TicketUpdateRequest request,
                                 Authentication auth) {
        return ticketService.update(id, request, auth);
    }
}
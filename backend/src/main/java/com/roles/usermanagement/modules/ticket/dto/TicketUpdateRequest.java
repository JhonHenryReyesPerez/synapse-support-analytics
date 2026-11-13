package com.roles.usermanagement.modules.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.roles.usermanagement.modules.ticket.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketUpdateRequest(

        @NotBlank(message = "El título es obligatorio")
        @Size(max = 300, message = "El título no puede superar 150 caracteres")
        String title,

        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 20000, message = "La descripción no puede superar 10.000 caracteres")
        String description,

        @Schema(description = "Id de una categoría activa (GET /api/categories). Tiene precedencia sobre category.")
        Long categoryId,
        @Schema(description = "Alternativa a categoryId: nombre de la categoría o valor del enum anterior (HARDWARE, REDES, NETWORK...)")
        String category,

        @NotNull(message = "La prioridad es obligatoria")
        TicketPriority priority
) {
}
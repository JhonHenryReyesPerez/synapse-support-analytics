package com.roles.usermanagement.modules.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.roles.usermanagement.modules.ticket.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketCreateRequest(

        @NotBlank(message = "the title is obligatory")
        @Size(min = 5, max = 150, message = "The title must be between 5 and 150 characters long.")
        String title,

        @NotBlank(message = "The description is mandatory.")
        @Size(min = 10, max = 2000, message = "The description must be between 10 and 2,000 characters long")
        String description,

        @Schema(description = "Id de una categoría activa (GET /api/categories). Tiene precedencia sobre category.")
        Long categoryId,
        @Schema(description = "Alternativa a categoryId: nombre de la categoría o valor del enum anterior (HARDWARE, REDES, NETWORK...)")
        String category,

        @NotNull(message = "The priority is mandatory.")
        TicketPriority priority
) {
}
package com.roles.usermanagement.modules.assistance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RateResponseRequest(
        @NotNull(message = "La calificación en estrellas es obligatoria")
        @Min(value = 1, message = "La calificación mínima es 1 estrella")
        @Max(value = 5, message = "La calificación máxima es 5 estrellas")
        Integer stars,

        @Size(max = 1000, message = "El comentario no puede superar los 1000 caracteres")
        String comment
) {}

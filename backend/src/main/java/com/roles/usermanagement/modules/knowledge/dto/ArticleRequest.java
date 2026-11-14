package com.roles.usermanagement.modules.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contenido técnico de un artículo. Estado, autor, verificación y versión los decide el servidor. */
public record ArticleRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 200, message = "El título no puede superar 200 caracteres")
        String title,
        @NotBlank(message = "El problema es obligatorio")
        @Size(max = 5000, message = "El problema no puede superar 5.000 caracteres")
        String problem,
        @NotBlank(message = "Los síntomas son obligatorios")
        @Size(max = 5000, message = "Los síntomas no pueden superar 5.000 caracteres")
        String symptoms,
        @NotBlank(message = "La causa es obligatoria")
        @Size(max = 5000, message = "La causa no puede superar 5.000 caracteres")
        String cause,
        @NotBlank(message = "El procedimiento de solución es obligatorio")
        @Size(max = 10000, message = "La solución no puede superar 10.000 caracteres")
        String solution
) {
}

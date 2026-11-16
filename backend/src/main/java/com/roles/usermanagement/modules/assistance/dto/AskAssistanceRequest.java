package com.roles.usermanagement.modules.assistance.dto;

import jakarta.validation.constraints.Size;

public record AskAssistanceRequest(
        @Size(max = 2000, message = "La pregunta adicional no puede superar los 2000 caracteres")
        String prompt
) {}

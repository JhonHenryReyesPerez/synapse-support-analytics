package com.roles.usermanagement.modules.assistance.dto;

import com.roles.usermanagement.modules.assistance.enums.AiQueryStatus;

import java.time.LocalDateTime;

public record AiQueryDto(
        Long id,
        Long ticketId,
        String requestedBy,
        String queryText,
        AiQueryStatus status,
        String errorMessage,
        LocalDateTime createdAt,
        AiResponseDto response
) {}

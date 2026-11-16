package com.roles.usermanagement.modules.assistance.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AiResponseDto(
        Long id,
        Long queryId,
        String summary,
        List<String> steps,
        String recommendations,
        Integer sourcesCount,
        String modelName,
        Long responseTimeMs,
        LocalDateTime createdAt,
        List<AiSourceDto> sources
) {}

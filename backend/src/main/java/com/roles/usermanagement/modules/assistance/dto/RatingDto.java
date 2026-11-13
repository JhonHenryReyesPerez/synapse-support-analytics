package com.roles.usermanagement.modules.assistance.dto;

import java.time.LocalDateTime;

public record RatingDto(
        Long id,
        Long responseId,
        String ratedBy,
        Integer stars,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

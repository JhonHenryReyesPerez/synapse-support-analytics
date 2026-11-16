package com.roles.usermanagement.modules.assistance.dto;

public record AiSourceDto(
        Long articleId,
        String articleTitle,
        Double similarityScore,
        Integer versionUsed
) {}

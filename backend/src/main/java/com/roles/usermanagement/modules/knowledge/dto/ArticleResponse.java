package com.roles.usermanagement.modules.knowledge.dto;

import com.roles.usermanagement.modules.knowledge.enums.IndexSyncStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record ArticleResponse(
        Long id,
        String title,
        String problem,
        String symptoms,
        String cause,
        String solution,
        String authorUsername,
        boolean verified,
        String verifiedByUsername,
        LocalDateTime verifiedAt,
        boolean active,
        @Schema(description = "true solo si active y verified: único caso en que el artículo alimenta el asistente")
        boolean eligible,
        int contentVersion,
        @Schema(description = "Sincronización con el índice vectorial; independiente del estado editorial")
        IndexSyncStatus indexStatus,
        Integer indexedVersion,
        LocalDateTime indexedAt,
        String indexErrorCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

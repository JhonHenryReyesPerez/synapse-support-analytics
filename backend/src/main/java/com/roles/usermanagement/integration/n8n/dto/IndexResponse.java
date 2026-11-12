package com.roles.usermanagement.integration.n8n.dto;

/** Contrato v1 n8n → Spring Boot: resultado de sincronizar un artículo. */
public record IndexResponse(
        Integer schemaVersion,
        Long articleId,
        Integer contentVersion,
        /** INDEXED (UPSERT correcto), REMOVED (DELETE correcto) o ERROR. */
        String status,
        String embeddingModel,
        Integer embeddingDimension,
        Integer chunkCount,
        /** Código seguro en caso de ERROR. */
        String errorCode
) {
}

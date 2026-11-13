package com.roles.usermanagement.integration.n8n.dto;

import java.time.LocalDateTime;

/**
 * Contrato v1 Spring Boot → n8n para sincronizar un artículo con Qdrant.
 * UPSERT: generar embeddings de la versión indicada y reemplazar los puntos del artículo
 * (identidad determinista por articleId + fragmento; retirar fragmentos de versiones anteriores).
 * DELETE: retirar todos los puntos del artículo; el contenido no se envía.
 */
public record IndexRequest(
        int schemaVersion,
        Action action,
        Long articleId,
        int contentVersion,
        String title,
        String problem,
        String symptoms,
        String cause,
        String solution,
        String contentHash,
        boolean eligible,
        LocalDateTime updatedAt
) {
    public enum Action { UPSERT, DELETE }
}

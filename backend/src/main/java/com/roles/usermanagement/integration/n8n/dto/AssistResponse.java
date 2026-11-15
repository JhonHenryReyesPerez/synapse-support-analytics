package com.roles.usermanagement.integration.n8n.dto;

import java.util.List;

/** Contrato v1 n8n → Spring Boot: resultado estructurado de una consulta de asistencia. */
public record AssistResponse(
        Integer schemaVersion,
        String requestId,
        Long queryId,
        /** ANSWERED, NO_RESULTS o ERROR. */
        String status,
        String answer,
        List<String> recommendedSteps,
        List<Source> sources,
        String embeddingModel,
        String generationModel,
        Double threshold,
        String similarityMetric,
        String workflowVersion,
        String errorCode
) {
    /** Artículo usado como evidencia, con la versión recuperada y su similitud. */
    public record Source(Long articleId, Integer contentVersion, Double score, List<String> chunkIds) {
    }
}

package com.roles.usermanagement.integration.n8n.dto;

/**
 * Contrato v1 Spring Boot → n8n para una consulta de asistencia. Contiene solo los datos mínimos:
 * no incluye el JWT, la identidad del usuario, comentarios internos ni datos personales.
 */
public record AssistRequest(
        int schemaVersion,
        /** Identificador único e idempotente de la consulta. */
        String requestId,
        Long queryId,
        Long incidentId,
        /** 1 en el primer intento; 2 en el único reintento por fuentes obsoletas. */
        int attempt,
        String question,
        String incidentTitle,
        String incidentDescription,
        String incidentCategory,
        /** Máximo de artículos a recuperar (objetivo: entre 3 y 5). */
        int maxResults,
        /** Umbral mínimo de similitud; no completar con artículos por debajo de este valor. */
        double minScore,
        String retrievalProfileId
) {
    public AssistRequest(int schemaVersion, String requestId, Long queryId, Long incidentId, int attempt, String question,
        String incidentTitle, String incidentDescription, String incidentCategory, int maxResults, double minScore) {
        this(schemaVersion,requestId,queryId,incidentId,attempt,question,incidentTitle,incidentDescription,incidentCategory,maxResults,minScore,null);
    }
}

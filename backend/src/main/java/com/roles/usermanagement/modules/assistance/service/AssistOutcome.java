package com.roles.usermanagement.modules.assistance.service;

import java.util.List;

/**
 * Resultado de una llamada al flujo de asistencia después de validar el contrato. Todavía no se ha
 * contrastado con la base relacional: esa comprobación ocurre al persistir.
 */
public sealed interface AssistOutcome {

    /** El flujo generó una respuesta y declara las fuentes que usó. */
    record Answered(String answer, List<String> steps, List<SourceRef> sources, Metadata metadata) implements AssistOutcome {
    }

    /** Ningún artículo superó el umbral: no hay orientación técnica. */
    record NoResults(Metadata metadata) implements AssistOutcome {
    }

    /** Fallo técnico o respuesta que no cumple el contrato, ya reducido a un código seguro. */
    record Failed(String errorCode) implements AssistOutcome {
    }

    /** Record encargado de referenciar la fuente mas asociada a la busqueda ejecutada */
    record SourceRef(long articleId, int contentVersion, double score) {
    }

    /** Record encargado de la mética de similitud de busqueda -> vectores */
    record Metadata(String embeddingModel, String generationModel, String similarityMetric, String workflowVersion) {
    }
}

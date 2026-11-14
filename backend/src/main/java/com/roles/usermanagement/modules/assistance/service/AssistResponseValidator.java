package com.roles.usermanagement.modules.assistance.service;

import com.roles.usermanagement.integration.n8n.N8nException;
import com.roles.usermanagement.integration.n8n.dto.AssistRequest;
import com.roles.usermanagement.integration.n8n.dto.AssistResponse;
import com.roles.usermanagement.modules.assistance.service.AssistOutcome.Answered;
import com.roles.usermanagement.modules.assistance.service.AssistOutcome.Failed;
import com.roles.usermanagement.modules.assistance.service.AssistOutcome.Metadata;
import com.roles.usermanagement.modules.assistance.service.AssistOutcome.NoResults;
import com.roles.usermanagement.modules.assistance.service.AssistOutcome.SourceRef;
import com.roles.usermanagement.modules.knowledge.service.KnowledgeIndexService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Valida la respuesta de n8n contra el contrato v1 antes de tocar la base de datos: correlación
 * con la consulta, estado permitido, tamaños, número de fuentes y similitudes. Una respuesta que
 * no cumple se rechaza completa: nunca se corrige ni se recorta para aprovecharla.
 */
public final class AssistResponseValidator {

    public static final int MAX_ANSWER = 20_000;
    public static final int MAX_STEPS = 20;
    public static final int MAX_STEP = 2_000;
    public static final int MAX_CHUNK_IDS = 50;
    /** El contrato v1 trabaja con similitud coseno: valores entre -1 y 1, mayor es más parecido. */
    public static final String SIMILARITY_METRIC = "cosine";
    private static final double TOLERANCE = 1e-9;

    private AssistResponseValidator() {
    }

    public static AssistOutcome validate(AssistRequest request, AssistResponse response) {
        if (response == null
                || !Integer.valueOf(request.schemaVersion()).equals(response.schemaVersion())
                || !Objects.equals(request.requestId(), response.requestId())
                || !Objects.equals(request.queryId(), response.queryId())
                || response.status() == null) {
            return invalid();
        }
        return switch (response.status()) {
            case "ERROR" -> new Failed(KnowledgeIndexService.safeCode(response.errorCode(), "ASSIST_FAILED"));
            case "NO_RESULTS" -> noResults(response);
            case "ANSWERED" -> answered(request, response);
            default -> invalid();
        };
    }

    private static AssistOutcome noResults(AssistResponse response) {
        // Sin fuentes no hay respaldo: cualquier texto que acompañe a NO_RESULTS se ignora.
        if (response.sources() != null && !response.sources().isEmpty()) {
            return invalid();
        }
        Metadata metadata = metadata(response);
        return metadata == null ? invalid() : new NoResults(metadata);
    }

    private static AssistOutcome answered(AssistRequest request, AssistResponse response) {
        Metadata metadata = metadata(response);
        String answer = response.answer() == null ? "" : response.answer().trim();
        if (metadata == null || answer.isEmpty() || answer.length() > MAX_ANSWER) {
            return invalid();
        }

        List<String> steps = new ArrayList<>();
        if (response.recommendedSteps() != null) {
            if (response.recommendedSteps().size() > MAX_STEPS) {
                return invalid();
            }
            for (String step : response.recommendedSteps()) {
                String clean = step == null ? "" : step.trim();
                if (clean.isEmpty() || clean.length() > MAX_STEP) {
                    return invalid();
                }
                steps.add(clean);
            }
        }

        // Una respuesta sin fuentes no está fundamentada; más fuentes de las pedidas incumple el contrato.
        List<AssistResponse.Source> sources = response.sources();
        if (sources == null || sources.isEmpty() || sources.size() > request.maxResults()) {
            return invalid();
        }
        List<SourceRef> refs = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (AssistResponse.Source source : sources) {
            if (source == null || source.articleId() == null || source.articleId() <= 0
                    || source.contentVersion() == null || source.contentVersion() < 1
                    || source.score() == null || !Double.isFinite(source.score())
                    || source.score() < -1 - TOLERANCE || source.score() > 1 + TOLERANCE
                    // Nunca se completa el número de fuentes con artículos por debajo del umbral.
                    || source.score() < request.minScore() - TOLERANCE
                    // Los fragmentos de un artículo llegan agrupados: un artículo, una fuente.
                    || !seen.add(source.articleId())
                    || !validChunkIds(source.chunkIds())) {
                return invalid();
            }
            // El margen de tolerancia solo absorbe el redondeo: lo que se guarda queda dentro de [-1, 1].
            double score = Math.max(-1.0, Math.min(1.0, source.score()));
            refs.add(new SourceRef(source.articleId(), source.contentVersion(), score));
        }
        return new Answered(answer, List.copyOf(steps), List.copyOf(refs), metadata);
    }

    /** Devuelve null si algún metadato es inválido. Los ausentes o en blanco se registran como null. */
    private static Metadata metadata(AssistResponse response) {
        String metric = clean(response.similarityMetric());
        if (tooLong(response.embeddingModel(), 120) || tooLong(response.generationModel(), 120)
                || tooLong(response.workflowVersion(), 60)
                || (metric != null && !SIMILARITY_METRIC.equalsIgnoreCase(metric))
                || (response.threshold() != null && !Double.isFinite(response.threshold()))) {
            return null;
        }
        return new Metadata(clean(response.embeddingModel()), clean(response.generationModel()),
                metric == null ? null : SIMILARITY_METRIC, clean(response.workflowVersion()));
    }

    private static boolean validChunkIds(List<String> chunkIds) {
        if (chunkIds == null) {
            return true;
        }
        return chunkIds.size() <= MAX_CHUNK_IDS
                && chunkIds.stream().allMatch(id -> id != null && !id.isBlank() && id.length() <= 100);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean tooLong(String value, int max) {
        return value != null && value.trim().length() > max;
    }

    private static Failed invalid() {
        return new Failed(N8nException.Kind.INVALID_RESPONSE.code());
    }
}

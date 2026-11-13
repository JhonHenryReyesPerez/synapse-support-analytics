package com.roles.usermanagement.modules.assistance.service;

import com.roles.usermanagement.modules.assistance.dto.AssistanceQueryResponse;
import com.roles.usermanagement.modules.assistance.entity.AssistanceQuery;
import com.roles.usermanagement.modules.assistance.entity.AssistanceResponse;
import com.roles.usermanagement.modules.assistance.entity.ResponseRating;
import com.roles.usermanagement.modules.assistance.entity.ResponseSource;
import com.roles.usermanagement.modules.assistance.repository.AssistanceQueryRepository;
import com.roles.usermanagement.modules.assistance.repository.AssistanceResponseRepository;
import com.roles.usermanagement.modules.assistance.repository.ResponseRatingRepository;
import com.roles.usermanagement.modules.knowledge.entity.KnowledgeArticle;
import com.roles.usermanagement.modules.ticket.entity.Ticket;
import com.roles.usermanagement.modules.ticket.repository.TicketRepository;
import com.roles.usermanagement.modules.ticket.service.TicketAccessPolicy;
import com.roles.usermanagement.web.dto.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lectura de consultas al asistente (RF08). Quien puede ver el incidente puede ver sus consultas;
 * un incidente cerrado conserva su historial de solo lectura.
 */
@Service
public class AssistanceHistoryService {

    /** Menos de tres fuentes válidas se informa como evidencia limitada; nunca se rellena. */
    static final int FULL_EVIDENCE_SOURCES = 3;

    private final AssistanceQueryRepository queries;
    private final AssistanceResponseRepository responses;
    private final ResponseRatingRepository ratings;
    private final TicketRepository tickets;
    private final TicketAccessPolicy policy;

    public AssistanceHistoryService(AssistanceQueryRepository queries, AssistanceResponseRepository responses,
                                    ResponseRatingRepository ratings, TicketRepository tickets, TicketAccessPolicy policy) {
        this.queries = queries;
        this.responses = responses;
        this.ratings = ratings;
        this.tickets = tickets;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public AssistanceQueryResponse getById(Long queryId, Authentication auth) {
        AssistanceQuery query = queries.findById(queryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La consulta no existe"));
        policy.assertCanView(query.getTicket(), auth);
        return toResponses(List.of(query)).get(0);
    }

    /** Historial de un incidente, de la consulta más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public PageResponse<AssistanceQueryResponse> listByTicket(Long ticketId, Integer page, Integer size, Authentication auth) {
        Ticket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La incidencia no existe"));
        policy.assertCanView(ticket, auth);
        Pageable pageable = PageResponse.request(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<AssistanceQuery> result = queries.findByTicketId(ticketId, pageable);
        return new PageResponse<>(toResponses(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    /** Las respuestas, sus fuentes y sus calificaciones se cargan en bloque para toda la página. */
    private List<AssistanceQueryResponse> toResponses(List<AssistanceQuery> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<Long> ids = page.stream().map(AssistanceQuery::getId).toList();
        Map<Long, AssistanceResponse> byQuery = responses.findWithSourcesByQueryIdIn(ids).stream()
                .collect(Collectors.toMap(response -> response.getQuery().getId(), Function.identity()));
        List<Long> responseIds = byQuery.values().stream().map(AssistanceResponse::getId).toList();
        // Solo quien hizo la consulta puede calificarla: hay como máximo una calificación por respuesta.
        Map<Long, ResponseRating> byResponse = responseIds.isEmpty() ? Map.of()
                : ratings.findByResponseIdIn(responseIds).stream()
                        .collect(Collectors.toMap(rating -> rating.getResponse().getId(), Function.identity(), (a, b) -> a));
        return page.stream().map(query -> {
            AssistanceResponse response = byQuery.get(query.getId());
            return toResponse(query, response, response == null ? null : byResponse.get(response.getId()));
        }).toList();
    }

    private static AssistanceQueryResponse toResponse(AssistanceQuery query, AssistanceResponse response, ResponseRating rating) {
        List<AssistanceQueryResponse.Source> sources = response == null ? List.of()
                : response.getSources().stream().map(AssistanceHistoryService::toSource).toList();
        boolean limited = response != null && sources.size() < FULL_EVIDENCE_SOURCES;
        return new AssistanceQueryResponse(
                query.getId(),
                query.getRequestId(),
                query.getTicket().getId(),
                query.getRequester().getUsername(),
                query.getQuestion(),
                query.getStatus(),
                query.getStatus().label(),
                message(query, sources.size(), limited),
                query.getErrorCode(),
                response == null ? null : response.getAnswer(),
                response == null ? List.of() : List.copyOf(response.getRecommendedSteps()),
                sources,
                limited,
                new AssistanceQueryResponse.Retrieval(query.getMaxResults(), query.getMinScore(),
                        query.getSimilarityMetric(), query.getEmbeddingModel()),
                response == null ? null : response.getGenerationModel(),
                query.getWorkflowVersion(),
                query.getAttempts(),
                query.getRetryOf() == null ? null : query.getRetryOf().getId(),
                rating == null ? null : RatingService.toResponse(query.getId(), rating),
                query.getCreatedAt(),
                query.getCompletedAt());
    }

    private static AssistanceQueryResponse.Source toSource(ResponseSource source) {
        KnowledgeArticle article = source.getArticle();
        // Histórica: el artículo cambió de versión o dejó de estar publicado después de la respuesta.
        boolean current = article.isEligible() && article.getContentVersion() == source.getArticleVersion();
        return new AssistanceQueryResponse.Source(article.getId(), source.getArticleTitle(), source.getArticleVersion(),
                source.getScore(), source.getSourceOrder(), source.getEvidence(), current);
    }

    private static String message(AssistanceQuery query, int sourceCount, boolean limited) {
        return switch (query.getStatus()) {
            case PENDING -> "La consulta está en proceso.";
            case ANSWERED -> "Respuesta basada en " + sourceCount
                    + (sourceCount == 1 ? " artículo verificado" : " artículos verificados") + " de la base de conocimiento."
                    + (limited ? " Evidencia limitada: menos de tres artículos superaron el umbral de similitud." : "");
            case NO_RESULTS -> "No se encontró información suficientemente relevante en la base de conocimiento "
                    + "para esta consulta. No se generó ninguna solución.";
            case ERROR -> "No fue posible completar la consulta por un fallo técnico. Esto no indica que falte "
                    + "información en la base de conocimiento; puedes intentarlo de nuevo.";
        };
    }
}

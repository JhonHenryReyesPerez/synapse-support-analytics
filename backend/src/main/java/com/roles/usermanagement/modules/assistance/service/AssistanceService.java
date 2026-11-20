package com.roles.usermanagement.modules.assistance.service;

import com.roles.usermanagement.integration.n8n.N8nClient;
import com.roles.usermanagement.integration.n8n.N8nException;
import com.roles.usermanagement.integration.n8n.N8nProperties;
import com.roles.usermanagement.integration.n8n.dto.AssistRequest;
import com.roles.usermanagement.modules.assistance.config.AssistanceProperties;
import com.roles.usermanagement.modules.assistance.dto.AssistanceQueryResponse;
import com.roles.usermanagement.modules.assistance.dto.AssistanceRequest;
import com.roles.usermanagement.modules.assistance.entity.AssistanceQuery;
import com.roles.usermanagement.modules.assistance.entity.AssistanceResponse;
import com.roles.usermanagement.modules.assistance.entity.ResponseSource;
import com.roles.usermanagement.modules.assistance.enums.AssistanceStatus;
import com.roles.usermanagement.modules.assistance.repository.AssistanceQueryRepository;
import com.roles.usermanagement.modules.assistance.repository.AssistanceResponseRepository;
import com.roles.usermanagement.modules.assistance.service.AssistOutcome.Answered;
import com.roles.usermanagement.modules.assistance.service.AssistOutcome.Failed;
import com.roles.usermanagement.modules.assistance.service.AssistOutcome.Metadata;
import com.roles.usermanagement.modules.assistance.service.AssistOutcome.NoResults;
import com.roles.usermanagement.modules.assistance.service.AssistOutcome.SourceRef;
import com.roles.usermanagement.modules.knowledge.entity.KnowledgeArticle;
import com.roles.usermanagement.modules.knowledge.repository.KnowledgeArticleRepository;
import com.roles.usermanagement.modules.lifecycle.enums.TicketStatus;
import com.roles.usermanagement.modules.ticket.entity.Ticket;
import com.roles.usermanagement.modules.ticket.repository.TicketRepository;
import com.roles.usermanagement.modules.ticket.service.TicketAccessPolicy;
import com.roles.usermanagement.persistance.crud.UserCrudRepository;
import com.roles.usermanagement.persistance.entity.UserEntity;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Caso de uso del asistente RAG (RF04, RF05, RF08). Toda consulta inteligente entra por aquí:
 *
 * <ol>
 *   <li>Transacción 1: autoriza el incidente, valida la pregunta y persiste la consulta en PENDING.</li>
 *   <li>Sin transacción: llama a n8n y valida el contrato de la respuesta.</li>
 *   <li>Transacción 2: contrasta las fuentes con la base relacional y fija el estado terminal.</li>
 * </ol>
 *
 * Ninguna transacción de base de datos queda abierta durante la llamada HTTP. El asistente solo
 * orienta: no cambia estados del incidente, no asigna técnicos y no verifica artículos.
 */
@Service
public class AssistanceService {

    /** Resultado de una solicitud: la consulta y si se creó ahora o ya existía (repetición idempotente). */
    public record Result(AssistanceQueryResponse query, boolean created) {
    }

    private record Opened(Long queryId, AssistRequest request) {
    }

    private enum Completion { DONE, STALE_SOURCES, ALREADY_TERMINAL }

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_QUESTION = 2000;
    /** Una llamada y, como máximo, un reintento cuando una fuente cambió durante la generación. */
    static final int MAX_ATTEMPTS = 2;
    /** Las fuentes siguieron cambiando tras el reintento: no se pudo construir una respuesta vigente. */
    public static final String SOURCES_CHANGED = "SOURCES_CHANGED";
    /** La consulta quedó PENDING más allá del tiempo límite (interrupción o reinicio). */
    public static final String ABANDONED = "ASSIST_ABANDONED";
    public static final String EMBEDDING_MODEL_MISMATCH = "EMBEDDING_MODEL_MISMATCH";
    static final String UNEXPECTED_ERROR = "ASSIST_UNEXPECTED_ERROR";

    private static final Logger log = LoggerFactory.getLogger(AssistanceService.class);

    private final TicketRepository tickets;
    private final UserCrudRepository users;
    private final TicketAccessPolicy policy;
    private final AssistanceQueryRepository queries;
    private final AssistanceResponseRepository responses;
    private final KnowledgeArticleRepository articles;
    private final AssistanceHistoryService history;
    private final N8nClient n8n;
    private final AssistanceProperties properties;
    private final TransactionTemplate transactions;
    private final com.roles.usermanagement.modules.rag.service.AdvancedRagService advanced;
    private final Duration advancedBudget;

    public AssistanceService(TicketRepository tickets, UserCrudRepository users, TicketAccessPolicy policy,
                             AssistanceQueryRepository queries, AssistanceResponseRepository responses,
                             KnowledgeArticleRepository articles, AssistanceHistoryService history, N8nClient n8n,
                             AssistanceProperties properties, N8nProperties n8nProperties,
                             PlatformTransactionManager transactionManager, com.roles.usermanagement.modules.rag.service.AdvancedRagService advanced) {
        this.advanced = advanced;
        this.tickets = tickets;
        this.users = users;
        this.policy = policy;
        this.queries = queries;
        this.responses = responses;
        this.articles = articles;
        this.history = history;
        this.n8n = n8n;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);

        this.advancedBudget=n8nProperties.connectTimeout().plus(n8nProperties.readTimeout()).multipliedBy(MAX_ATTEMPTS * 2L);
        // Una consulta en curso no debe darse por abandonada: el límite tiene que cubrir sus dos intentos.
        Duration worstCase = n8nProperties.connectTimeout().plus(n8nProperties.readTimeout()).multipliedBy(MAX_ATTEMPTS);
        if (properties.pendingTimeout().compareTo(worstCase) <= 0) {
            throw new IllegalStateException("ASSIST_PENDING_TIMEOUT (" + properties.pendingTimeout()
                    + ") debe ser mayor que " + worstCase + ": dos intentos con N8N_CONNECT_TIMEOUT + N8N_READ_TIMEOUT");
        }
    }

    public Result ask(Long ticketId, AssistanceRequest body, Authentication auth) {
        Opened opened = Objects.requireNonNull(transactions.execute(status -> open(ticketId, body, auth)));
        if (opened.request() == null) {
            return new Result(history.getById(opened.queryId(), auth), false);
        }

        long start = System.nanoTime();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            AssistRequest request = withAttempt(opened.request(), attempt);
            AssistOutcome outcome = call(request, auth.getName());
            boolean lastAttempt = attempt == MAX_ATTEMPTS;
            Completion completion;
            try {
                completion = transactions.execute(status -> complete(request, outcome, lastAttempt));
            } catch (RuntimeException e) {
                log.error("No se pudo registrar el resultado de la consulta {}", opened.queryId(), e);
                transactions.executeWithoutResult(status -> fail(opened.queryId(), UNEXPECTED_ERROR));
                break;
            }
            if (completion != Completion.STALE_SOURCES) {
                break;
            }

            

            log.info("asistencia requestId={} queryId={} fuentes obsoletas en el intento {}",
                    request.requestId(), request.queryId(), attempt);
        }

        AssistanceQueryResponse view = history.getById(opened.queryId(), auth);
        // Se registran correlación, estado y duración; nunca la pregunta ni la respuesta.
        log.info("asistencia requestId={} queryId={} estado={} intentos={} ms={}", view.requestId(), view.id(),
                view.status(), view.attempts(), (System.nanoTime() - start) / 1_000_000);
        if (view.status() == AssistanceStatus.ERROR) {
            throw new AssistanceFailedException(view.id(), view.requestId(), view.errorCode());
        }
        return new Result(view, true);
    }

    /** Transacción 1: autorización, validación y persistencia de la consulta en PENDING. */
    private Opened open(Long ticketId, AssistanceRequest body, Authentication auth) {
        // El bloqueo del incidente serializa las solicitudes simultáneas sobre el mismo ticket.
        Ticket ticket = tickets.findForUpdate(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La incidencia no existe"));
        policy.assertCanView(ticket, auth);

        String requestId = body == null || body.requestId() == null ? null : normalizeRequestId(body.requestId());
        if (requestId != null) {
            Optional<AssistanceQuery> existing = queries.findByRequestId(requestId);
            if (existing.isPresent()) {
                AssistanceQuery query = existing.get();
                if (!query.getTicket().getId().equals(ticketId)
                        || !query.getRequester().getUsername().equals(auth.getName())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "El requestId ya pertenece a otra consulta");
                }
                return new Opened(query.getId(), null);
            }
        }

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La incidencia está cerrada: no admite nuevas consultas al asistente");
        }

        // Primero se cierran las consultas abandonadas: no deben bloquear una solicitud nueva ni un reintento.
        LocalDateTime now = LocalDateTime.now();
        queries.expirePending(now.minus(properties.pendingTimeout()), ABANDONED, now);

        AssistanceQuery retryOf = body == null || body.retryOfQueryId() == null ? null : retryTarget(body.retryOfQueryId(), ticketId);
        String question = resolveQuestion(body, ticket, retryOf);
        if (queries.existsByTicketIdAndRequesterUsernameAndStatus(ticketId, auth.getName(), AssistanceStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya tienes una consulta en proceso para esta incidencia; espera su resultado");
        }

        UserEntity requester = users.findById(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario no existe"));
        AssistanceQuery query = new AssistanceQuery();
        query.setTicket(ticket);
        query.setRequester(requester);
        query.setQuestion(question);
        query.setRetrievalProfile(body!=null && body.retrievalProfileId()!=null ? body.retrievalProfileId() : retryOf==null?null:retryOf.getRetrievalProfile());
        if(query.getRetrievalProfile()!=null) {
            if(properties.pendingTimeout().compareTo(advancedBudget)<=0)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "El timeout pendiente debe cubrir recuperación y generación en dos intentos para usar perfiles RAG");
        }
        query.setStatus(AssistanceStatus.PENDING);
        query.setRequestId(requestId == null ? UUID.randomUUID().toString() : requestId);
        query.setMaxResults(properties.maxResults());
        query.setMinScore(query.getRetrievalProfile()==null?properties.minScore():advanced.minimumCosine(query.getRetrievalProfile()));
        query.setAttempts(0);
        query.setRetryOf(retryOf);
        query.setCreatedAt(now);
        queries.save(query);

        // Datos mínimos: sin identidad del usuario, sin comentarios y sin el token.
        return new Opened(query.getId(), new AssistRequest(SCHEMA_VERSION, query.getRequestId(), query.getId(),
                ticket.getId(), 1, question, ticket.getTitle(), ticket.getDescription(), ticket.getCategory().getName(),
                query.getMaxResults(), query.getMinScore(), query.getRetrievalProfile()));
    }

    private AssistanceQuery retryTarget(Long queryId, Long ticketId) {
        AssistanceQuery previous = queries.findById(queryId)
                .filter(query -> query.getTicket().getId().equals(ticketId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "La consulta que se reintenta no existe en esta incidencia"));
        if (!previous.getStatus().isTerminal()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La consulta que se reintenta aún está en proceso");
        }
        return previous;
    }

    private static String resolveQuestion(AssistanceRequest body, Ticket ticket, AssistanceQuery retryOf) {
        if (body == null || body.question() == null) {
            // Sin pregunta explícita se consulta con la del intento anterior o con la descripción del incidente.
            return retryOf != null ? retryOf.getQuestion() : ticket.getDescription().trim();
        }
        String question = body.question().trim();
        if (question.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La pregunta no puede estar vacía");
        }
        if (question.length() > MAX_QUESTION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La pregunta no puede superar " + MAX_QUESTION + " caracteres");
        }
        return question;
    }

    private static String normalizeRequestId(String candidate) {
        try {
            String normalized = UUID.fromString(candidate.trim()).toString();
            if (normalized.equalsIgnoreCase(candidate.trim())) {
                return normalized;
            }
        } catch (IllegalArgumentException ignored) {
            // Se responde 400 a continuación.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId debe ser un UUID");
    }

    /** Llamada a n8n fuera de toda transacción. Cualquier fallo se reduce a un código seguro. */
    private AssistOutcome call(AssistRequest request, String actor) {
        try {
            return request.retrievalProfileId()==null ? AssistResponseValidator.validate(request, n8n.assist(request)) : advanced.answer(request, actor);
        } catch (N8nException e) {
            return new Failed(e.code());
        } catch (RuntimeException e) {
            log.error("Fallo inesperado en la consulta {}", request.queryId(), e);
            return new Failed(UNEXPECTED_ERROR);
        }
    }

    /**
     * Transacción 2: fija el estado terminal. Solo actúa si la consulta sigue PENDING, de modo que
     * un resultado tardío no sobrescribe una consulta que ya terminó. Las fuentes se bloquean en
     * lectura mientras se comprueban: al confirmar, todas existen, están publicadas y en la versión citada.
     */
    private Completion complete(AssistRequest request, AssistOutcome outcome, boolean lastAttempt) {
        AssistanceQuery query = queries.findForUpdate(request.queryId()).orElse(null);
        if (query == null || query.getStatus() != AssistanceStatus.PENDING) {
            return Completion.ALREADY_TERMINAL;
        }
        query.setAttempts(request.attempt());
        if (outcome instanceof Failed failed) {
            return finish(query, AssistanceStatus.ERROR, failed.errorCode());
        }
        if (outcome instanceof NoResults noResults) {
            applyMetadata(query, noResults.metadata());
            return finish(query, AssistanceStatus.NO_RESULTS, null);
        }

        Answered answered = (Answered) outcome;
        List<Long> ids = answered.sources().stream().map(SourceRef::articleId).sorted().toList();
        Map<Long, KnowledgeArticle> found = articles.findAllByIdForShare(ids).stream()
                .collect(Collectors.toMap(KnowledgeArticle::getId, Function.identity()));
        if (found.size() != ids.size()) {
            // Un artículo que no existe en la base relacional no es una fuente: la respuesta es inválida.
            return finish(query, AssistanceStatus.ERROR, N8nException.Kind.INVALID_RESPONSE.code());
        }
        boolean stale = answered.sources().stream().anyMatch(source -> {
            KnowledgeArticle article = found.get(source.articleId());
            return !article.isEligible() || article.getContentVersion() != source.contentVersion();
        });
        if (stale) {
            // No basta con quitar la cita: el texto se generó con esa fuente y se descarta completo.
            return lastAttempt ? finish(query, AssistanceStatus.ERROR, SOURCES_CHANGED) : Completion.STALE_SOURCES;
        }
        Metadata metadata = answered.metadata();
        boolean otherModel = request.retrievalProfileId()==null && metadata.embeddingModel() != null && found.values().stream()
                .anyMatch(article -> article.getEmbeddingModel() != null
                        && !article.getEmbeddingModel().equals(metadata.embeddingModel()));
        if (otherModel) {
            // La consulta debe usar el mismo modelo de embeddings con el que se indexaron los artículos.
            return finish(query, AssistanceStatus.ERROR, EMBEDDING_MODEL_MISMATCH);
        }

        AssistanceResponse response = new AssistanceResponse();
        response.setQuery(query);
        query.setAdvancedJson(answered.detailsJson());
        response.setAnswer(answered.answer());
        response.getRecommendedSteps().addAll(answered.steps());
        response.setGenerationModel(metadata.generationModel());
        response.setCreatedAt(LocalDateTime.now());
        List<SourceRef> ranked = request.retrievalProfileId()!=null ? answered.sources() : answered.sources().stream()
                .sorted(Comparator.comparingDouble(SourceRef::score).reversed().thenComparingLong(SourceRef::articleId))
                .toList();
        for (int i = 0; i < ranked.size(); i++) {
            SourceRef ref = ranked.get(i);
            KnowledgeArticle article = found.get(ref.articleId());
            ResponseSource source = new ResponseSource();
            source.setResponse(response);
            source.setArticle(article);
            source.setArticleVersion(article.getContentVersion());
            source.setScore(ref.score());
            source.setSourceOrder(i + 1);
            source.setArticleTitle(article.getTitle());
            source.setEvidence(ref.evidence()==null?evidenceOf(article):ref.evidence());
            source.setContentHash(article.getContentHash());
            response.getSources().add(source);
        }
        responses.save(response);
        applyMetadata(query, metadata);
        return finish(query, AssistanceStatus.ANSWERED, null);
    }

    private void fail(Long queryId, String errorCode) {
        queries.findForUpdate(queryId)
                .filter(query -> query.getStatus() == AssistanceStatus.PENDING)
                .ifPresent(query -> finish(query, AssistanceStatus.ERROR, errorCode));
    }

    private static Completion finish(AssistanceQuery query, AssistanceStatus status, String errorCode) {
        query.setStatus(status);
        query.setErrorCode(errorCode);
        query.setCompletedAt(LocalDateTime.now());
        return Completion.DONE;
    }

    private static void applyMetadata(AssistanceQuery query, Metadata metadata) {
        query.setEmbeddingModel(metadata.embeddingModel());
        query.setSimilarityMetric(metadata.similarityMetric());
        query.setWorkflowVersion(metadata.workflowVersion());
    }

    private static String evidenceOf(KnowledgeArticle article) {
        return "Problema: " + article.getProblem() + "\n\nSíntomas: " + article.getSymptoms()
                + "\n\nCausa: " + article.getCause() + "\n\nSolución: " + article.getSolution();
    }

    private static AssistRequest withAttempt(AssistRequest request, int attempt) {
        return new AssistRequest(request.schemaVersion(), request.requestId(), request.queryId(), request.incidentId(),
                attempt, request.question(), request.incidentTitle(), request.incidentDescription(),
                request.incidentCategory(), request.maxResults(), request.minScore(), request.retrievalProfileId());
    }
}

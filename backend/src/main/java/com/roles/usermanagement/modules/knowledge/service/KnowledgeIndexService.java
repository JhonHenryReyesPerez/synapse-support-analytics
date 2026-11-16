package com.roles.usermanagement.modules.knowledge.service;

import com.roles.usermanagement.integration.n8n.N8nClient;
import com.roles.usermanagement.integration.n8n.N8nException;
import com.roles.usermanagement.integration.n8n.dto.IndexRequest;
import com.roles.usermanagement.integration.n8n.dto.IndexResponse;
import com.roles.usermanagement.modules.knowledge.dto.IndexSyncSummary;
import com.roles.usermanagement.modules.knowledge.entity.KnowledgeArticle;
import com.roles.usermanagement.modules.knowledge.enums.IndexSyncStatus;
import com.roles.usermanagement.modules.knowledge.repository.KnowledgeArticleRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sincroniza artículos con el índice vectorial a través de n8n (RF03): Spring Boot → n8n →
 * embeddings → Qdrant. La llamada HTTP ocurre fuera de toda transacción: primero se lee una
 * instantánea, después se llama a n8n y por último se registra el resultado solo si el artículo
 * no cambió entre tanto. Un fallo nunca altera el artículo relacional ni lo marca como indexado.
 */
@Service
public class KnowledgeIndexService {

    public enum Outcome { INDEXED, REMOVED, FAILED, SKIPPED }

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexService.class);
    static final int SCHEMA_VERSION = 1;
    /** Tope de artículos por reconciliación; las siguientes llamadas continúan con el resto. */
    static final int RECONCILE_BATCH = 50;

    private final KnowledgeArticleRepository articles;
    private final N8nClient n8n;
    private final TransactionTemplate transactions;

    public KnowledgeIndexService(KnowledgeArticleRepository articles, N8nClient n8n,
                                 PlatformTransactionManager transactionManager) {
        this.articles = articles;
        this.n8n = n8n;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Lleva el índice al estado que exige el artículo: indexar la versión vigente si es elegible o
     * retirar sus vectores si no lo es. Hace un único intento; los reintentos son explícitos
     * (reindexar o reconciliar). Repetirlo sin cambios no duplica puntos: la identidad en el índice
     * es determinista por artículo y fragmento.
     */
    public Outcome sync(Long articleId) {
        IndexRequest request = transactions.execute(status -> articles.findById(articleId)
                .filter(article -> article.getIndexStatus() == IndexSyncStatus.PENDING
                        || article.getIndexStatus() == IndexSyncStatus.ERROR)
                .map(KnowledgeIndexService::toRequest)
                .orElse(null));
        if (request == null) {
            return Outcome.SKIPPED;
        }

        IndexResponse response = null;
        String errorCode = null;
        try {
            response = n8n.index(request);
            errorCode = validate(request, response);
        } catch (N8nException e) {
            errorCode = e.code();
        } catch (RuntimeException e) {
            log.error("Fallo inesperado sincronizando el artículo {}", articleId, e);
            errorCode = "INDEX_UNEXPECTED_ERROR";
        }

        IndexResponse result = response;
        String failure = errorCode;
        return transactions.execute(status -> record(request, result, failure));
    }

    /** Sincroniza los artículos pendientes o con error, hasta el tope por llamada. */
    public IndexSyncSummary reconcile() {
        List<Long> ids = transactions.execute(status -> articles
                .findByIndexStatusInOrderByIdAsc(List.of(IndexSyncStatus.PENDING, IndexSyncStatus.ERROR)).stream()
                .limit(RECONCILE_BATCH)
                .map(KnowledgeArticle::getId)
                .toList());
        int indexed = 0;
        int removed = 0;
        int failed = 0;
        int skipped = 0;
        for (Long id : ids) {
            switch (sync(id)) {
                case INDEXED -> indexed++;
                case REMOVED -> removed++;
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
            }
        }
        return new IndexSyncSummary(ids.size(), indexed, removed, failed, skipped);
    }

    private Outcome record(IndexRequest request, IndexResponse response, String errorCode) {
        KnowledgeArticle article = articles.findForUpdate(request.articleId()).orElse(null);
        if (article == null) {
            return Outcome.SKIPPED;
        }
        // El artículo cambió durante la llamada: el resultado ya no corresponde a su estado vigente.
        if (article.getContentVersion() != request.contentVersion() || article.isEligible() != request.eligible()) {
            if (request.action() == IndexRequest.Action.UPSERT) {
                // El índice pudo recibir vectores de una versión que ya no es elegible: queda pendiente retirarlos.
                article.setIndexedVersion(request.contentVersion());
            }
            // También tras un DELETE tardío: pudo borrar el vector de un artículo que ya se reactivó.
            article.setIndexStatus(IndexSyncStatus.PENDING);
            return Outcome.SKIPPED;
        }
        if (errorCode == null && request.action() == IndexRequest.Action.UPSERT
                && articles.existsIndexedWithDifferentEmbedding(article.getId(),
                        response.embeddingModel(), response.embeddingDimension())) {
            // No se mezclan vectores de modelos o dimensiones distintos en la misma colección.
            errorCode = "EMBEDDING_MODEL_MISMATCH";
        }
        if (errorCode != null) {
            article.setIndexStatus(IndexSyncStatus.ERROR);
            article.setIndexErrorCode(errorCode);
            return Outcome.FAILED;
        }
        article.setIndexErrorCode(null);
        if (request.action() == IndexRequest.Action.UPSERT) {
            article.setIndexStatus(IndexSyncStatus.INDEXED);
            article.setIndexedVersion(request.contentVersion());
            article.setIndexedAt(LocalDateTime.now());
            article.setEmbeddingModel(response.embeddingModel());
            article.setEmbeddingDimension(response.embeddingDimension());
            return Outcome.INDEXED;
        }
        article.setIndexStatus(IndexSyncStatus.NOT_INDEXED);
        article.setIndexedVersion(null);
        article.setIndexedAt(null);
        return Outcome.REMOVED;
    }

    /** Devuelve un código de error si la respuesta no corresponde a la solicitud o no cumple el contrato. */
    private static String validate(IndexRequest request, IndexResponse response) {
        if (response == null
                || !Objects.equals(response.articleId(), request.articleId())
                || !Objects.equals(response.contentVersion(), request.contentVersion())
                || response.status() == null) {
            return N8nException.Kind.INVALID_RESPONSE.code();
        }
        if ("ERROR".equals(response.status())) {
            return safeCode(response.errorCode(), "INDEX_FAILED");
        }
        if (request.action() == IndexRequest.Action.UPSERT) {
            boolean complete = "INDEXED".equals(response.status())
                    && response.embeddingModel() != null && !response.embeddingModel().isBlank()
                    && response.embeddingModel().length() <= 120
                    && response.embeddingDimension() != null && response.embeddingDimension() > 0;
            return complete ? null : N8nException.Kind.INVALID_RESPONSE.code();
        }
        return "REMOVED".equals(response.status()) ? null : N8nException.Kind.INVALID_RESPONSE.code();
    }

    /** Solo se conserva un código con formato de identificador; cualquier otro texto se descarta. */
    public static String safeCode(String candidate, String fallback) {
        return candidate != null && candidate.matches("[A-Z][A-Z0-9_]{2,59}") ? candidate : fallback;
    }

    private static IndexRequest toRequest(KnowledgeArticle article) {
        boolean eligible = article.isEligible();
        if (!eligible) {
            return new IndexRequest(SCHEMA_VERSION, IndexRequest.Action.DELETE, article.getId(), article.getContentVersion(),
                    null, null, null, null, null, article.getContentHash(), false, article.getUpdatedAt());
        }
        return new IndexRequest(SCHEMA_VERSION, IndexRequest.Action.UPSERT, article.getId(), article.getContentVersion(),
                article.getTitle(), article.getProblem(), article.getSymptoms(), article.getCause(), article.getSolution(),
                article.getContentHash(), true, article.getUpdatedAt());
    }
}

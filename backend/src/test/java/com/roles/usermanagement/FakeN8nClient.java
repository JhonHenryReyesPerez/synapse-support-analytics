package com.roles.usermanagement;

import com.roles.usermanagement.integration.n8n.N8nException;

import com.roles.usermanagement.integration.n8n.N8nClient;
import com.roles.usermanagement.integration.n8n.dto.AssistRequest;
import com.roles.usermanagement.integration.n8n.dto.AssistResponse;
import com.roles.usermanagement.integration.n8n.dto.IndexRequest;
import com.roles.usermanagement.integration.n8n.dto.IndexResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Doble determinista del cliente de n8n para las pruebas: registra las llamadas y permite
 * programar la respuesta. Las pruebas integradas contra workflows reales van aparte.
 */
@Component
@Primary
public class FakeN8nClient implements N8nClient {

    public static final String EMBEDDING_MODEL = "test-embedding-model";
    public static final int EMBEDDING_DIMENSION = 8;

    public final List<IndexRequest> indexCalls = new CopyOnWriteArrayList<>();
    public final List<AssistRequest> assistCalls = new CopyOnWriteArrayList<>();
    public volatile Function<IndexRequest, IndexResponse> onIndex;
    public volatile Function<AssistRequest, AssistResponse> onAssist;

    public FakeN8nClient() {
        reset();
    }

    public void reset() {
        indexCalls.clear();
        assistCalls.clear();
        onIndex = FakeN8nClient::indexed;
        onAssist = FakeN8nClient::noResults;
        onKnowledge = request -> { throw new N8nException(N8nException.Kind.NOT_CONFIGURED); };
    }

    public java.util.function.Function<java.util.Map<String,Object>,java.util.Map<String,Object>> onKnowledge = request -> {
        throw new N8nException(N8nException.Kind.NOT_CONFIGURED);
    };
    @Override public java.util.Map<String,Object> knowledge(java.util.Map<String,Object> request) { return onKnowledge.apply(request); }

    @Override
    public IndexResponse index(IndexRequest request) {
        indexCalls.add(request);
        return onIndex.apply(request);
    }

    @Override
    public AssistResponse assist(AssistRequest request) {
        assistCalls.add(request);
        return onAssist.apply(request);
    }

    public static IndexResponse indexed(IndexRequest request) {
        boolean upsert = request.action() == IndexRequest.Action.UPSERT;
        return new IndexResponse(1, request.articleId(), request.contentVersion(), upsert ? "INDEXED" : "REMOVED",
                upsert ? EMBEDDING_MODEL : null, upsert ? EMBEDDING_DIMENSION : null, upsert ? 1 : 0, null);
    }

    public static AssistResponse noResults(AssistRequest request) {
        return new AssistResponse(1, request.requestId(), request.queryId(), "NO_RESULTS", null, null, List.of(),
                EMBEDDING_MODEL, "test-generation-model", request.minScore(), "cosine", "test-workflow-1", null);
    }

    public static AssistResponse answered(AssistRequest request, String answer, List<AssistResponse.Source> sources) {
        return new AssistResponse(1, request.requestId(), request.queryId(), "ANSWERED", answer,
                List.of("Paso 1", "Paso 2"), sources, EMBEDDING_MODEL, "test-generation-model", request.minScore(),
                "cosine", "test-workflow-1", null);
    }
}

package com.roles.usermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.roles.usermanagement.integration.n8n.N8nException;
import com.roles.usermanagement.integration.n8n.dto.IndexRequest;
import com.roles.usermanagement.integration.n8n.dto.IndexResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RF03: sincronización del índice vectorial mediante n8n y contrato de servicio para n8n. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KnowledgeIndexTests extends ApiTestSupport {

    @Autowired
    private FakeN8nClient n8n;

    @BeforeEach
    @AfterEach
    void resetN8n() {
        n8n.reset();
    }

    @Test
    void activationIndexesCurrentVersionAndRepeatingDoesNotChangeIdentity() throws Exception {
        String admin = adminToken();

        long id = publishArticle("Wi-Fi sin acceso", "Olvidar la red y volver a conectarse");

        List<IndexRequest> calls = callsFor(id);
        assertThat(calls).hasSize(1);
        IndexRequest sent = calls.get(0);
        assertThat(sent.schemaVersion()).isEqualTo(1);
        assertThat(sent.action()).isEqualTo(IndexRequest.Action.UPSERT);
        assertThat(sent.contentVersion()).isEqualTo(1);
        assertThat(sent.eligible()).isTrue();
        assertThat(sent.title()).isEqualTo("Wi-Fi sin acceso");
        assertThat(sent.solution()).isEqualTo("Olvidar la red y volver a conectarse");
        assertThat(sent.contentHash()).matches("[0-9a-f]{64}");
        JsonNode indexed = article(id, admin);
        assertThat(indexed.get("indexStatus").asText()).isEqualTo("INDEXED");
        assertThat(indexed.get("indexedVersion").asInt()).isEqualTo(1);

        // Reindexar sin cambios envía la misma identidad (artículo, versión, hash): el upsert no duplica.
        assertThat(request("POST", "/api/knowledge/articles/" + id + "/reindex", null, admin).statusCode()).isEqualTo(200);
        calls = callsFor(id);
        assertThat(calls).hasSize(2);
        assertThat(calls.get(1).contentVersion()).isEqualTo(sent.contentVersion());
        assertThat(calls.get(1).contentHash()).isEqualTo(sent.contentHash());
        assertThat(article(id, admin).get("indexStatus").asText()).isEqualTo("INDEXED");

        // Sin pendientes, reconciliar no llama a n8n por este artículo.
        request("POST", "/api/knowledge/articles/index/reconcile", null, admin);
        assertThat(callsFor(id)).hasSize(2);
    }

    @Test
    void deactivationAndContentChangeRemoveVectorsOfPreviousVersion() throws Exception {
        String admin = adminToken();
        long id = publishArticle("Correo no sincroniza", "Reconfigurar la cuenta");

        JsonNode inactive = json(request("POST", "/api/knowledge/articles/" + id + "/deactivate", null, admin));

        IndexRequest removal = callsFor(id).get(1);
        assertThat(removal.action()).isEqualTo(IndexRequest.Action.DELETE);
        assertThat(removal.eligible()).isFalse();
        assertThat(removal.title()).isNull();
        assertThat(removal.solution()).isNull();
        assertThat(inactive.get("indexStatus").asText()).isEqualTo("NOT_INDEXED");
        assertThat(inactive.get("indexedVersion").isNull()).isTrue();

        // Reactivar indexa de nuevo; cambiar el contenido retira la versión 1 antes de publicar la 2.
        request("POST", "/api/knowledge/articles/" + id + "/activate", null, admin);
        String changed = mapper.createObjectNode().put("title", "Correo no sincroniza").put("problem", "p").put("symptoms", "s")
                .put("cause", "c").put("solution", "Eliminar y agregar la cuenta").toString();
        JsonNode edited = json(request("PUT", "/api/knowledge/articles/" + id, changed, admin));
        assertThat(edited.get("contentVersion").asInt()).isEqualTo(2);
        assertThat(edited.get("eligible").asBoolean()).isFalse();
        assertThat(edited.get("indexStatus").asText()).isEqualTo("NOT_INDEXED");
        IndexRequest cleanup = callsFor(id).get(callsFor(id).size() - 1);
        assertThat(cleanup.action()).isEqualTo(IndexRequest.Action.DELETE);

        request("POST", "/api/knowledge/articles/" + id + "/verify", null, admin);
        request("POST", "/api/knowledge/articles/" + id + "/activate", null, admin);
        IndexRequest second = callsFor(id).get(callsFor(id).size() - 1);
        assertThat(second.action()).isEqualTo(IndexRequest.Action.UPSERT);
        assertThat(second.contentVersion()).isEqualTo(2);
        assertThat(article(id, admin).get("indexedVersion").asInt()).isEqualTo(2);
    }

    @Test
    void indexingFailureKeepsArticleAndIsRecoveredByReconciliation() throws Exception {
        String admin = adminToken();
        n8n.onIndex = request -> {
            throw new N8nException(N8nException.Kind.TIMEOUT);
        };

        long id = publishArticle("Pantalla azul", "Actualizar el controlador de video");

        // El fallo no revierte la publicación ni marca el artículo como indexado.
        JsonNode failed = article(id, admin);
        assertThat(failed.get("eligible").asBoolean()).isTrue();
        assertThat(failed.get("indexStatus").asText()).isEqualTo("ERROR");
        assertThat(failed.get("indexErrorCode").asText()).isEqualTo("N8N_TIMEOUT");
        assertThat(failed.get("indexedVersion").isNull()).isTrue();

        // n8n informa un error del proveedor: se conserva solo un código seguro.
        n8n.onIndex = request -> new IndexResponse(1, request.articleId(), request.contentVersion(), "ERROR",
                null, null, null, "clave sk-secreta rechazada por el proveedor");
        request("POST", "/api/knowledge/articles/" + id + "/reindex", null, admin);
        assertThat(article(id, admin).get("indexErrorCode").asText()).isEqualTo("INDEX_FAILED");

        // Respuesta de otro artículo: se rechaza.
        n8n.onIndex = request -> new IndexResponse(1, request.articleId() + 1, request.contentVersion(), "INDEXED",
                FakeN8nClient.EMBEDDING_MODEL, FakeN8nClient.EMBEDDING_DIMENSION, 1, null);
        request("POST", "/api/knowledge/articles/" + id + "/reindex", null, admin);
        assertThat(article(id, admin).get("indexErrorCode").asText()).isEqualTo("N8N_INVALID_RESPONSE");

        // Vectores de otro modelo no se mezclan con los ya indexados.
        publishArticleQuietly("Referencia de modelo");
        n8n.onIndex = request -> new IndexResponse(1, request.articleId(), request.contentVersion(), "INDEXED",
                "otro-modelo", 1024, 1, null);
        request("POST", "/api/knowledge/articles/" + id + "/reindex", null, admin);
        assertThat(article(id, admin).get("indexErrorCode").asText()).isEqualTo("EMBEDDING_MODEL_MISMATCH");

        // Recuperado el servicio, la reconciliación resuelve el pendiente.
        n8n.onIndex = FakeN8nClient::indexed;
        JsonNode summary = json(request("POST", "/api/knowledge/articles/index/reconcile", null, admin));
        assertThat(summary.get("indexed").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(summary.get("failed").asInt()).isZero();
        JsonNode recovered = article(id, admin);
        assertThat(recovered.get("indexStatus").asText()).isEqualTo("INDEXED");
        assertThat(recovered.get("indexErrorCode").isNull()).isTrue();
    }

    @Test
    void articleChangedDuringIndexingIsNotMarkedAsIndexed() throws Exception {
        String admin = adminToken();
        String body = KnowledgeArticleTests.body("Carrera de estados", "v1");
        long id = json(request("POST", "/api/knowledge/articles", body, admin)).get("id").asLong();
        request("POST", "/api/knowledge/articles/" + id + "/verify", null, admin);
        // Mientras n8n indexa, un administrador desactiva el artículo.
        n8n.onIndex = request -> {
            n8n.onIndex = FakeN8nClient::indexed;
            try {
                this.request("POST", "/api/knowledge/articles/" + id + "/deactivate", null, admin);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return FakeN8nClient.indexed(request);
        };

        request("POST", "/api/knowledge/articles/" + id + "/activate", null, admin);

        JsonNode result = article(id, admin);
        assertThat(result.get("active").asBoolean()).isFalse();
        assertThat(result.get("indexStatus").asText()).isEqualTo("PENDING");
        // La reconciliación retira los vectores que pudieron quedar.
        request("POST", "/api/knowledge/articles/index/reconcile", null, admin);
        assertThat(article(id, admin).get("indexStatus").asText()).isEqualTo("NOT_INDEXED");
        assertThat(callsFor(id).get(callsFor(id).size() - 1).action()).isEqualTo(IndexRequest.Action.DELETE);
    }

    @Test
    void articleReactivatedDuringRemovalIsIndexedAgainByReconciliation() throws Exception {
        String admin = adminToken();
        long id = publishArticle("Borrado tardío", "Reiniciar el servicio");
        // Mientras n8n borra el vector, un administrador reactiva el artículo y este se indexa.
        n8n.onIndex = request -> {
            n8n.onIndex = FakeN8nClient::indexed;
            try {
                this.request("POST", "/api/knowledge/articles/" + id + "/activate", null, admin);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return FakeN8nClient.indexed(request);
        };

        request("POST", "/api/knowledge/articles/" + id + "/deactivate", null, admin);

        // El borrado llegó después del upsert: el vector pudo perderse, así que no puede quedar INDEXED.
        JsonNode result = article(id, admin);
        assertThat(result.get("active").asBoolean()).isTrue();
        assertThat(result.get("indexStatus").asText()).isEqualTo("PENDING");
        request("POST", "/api/knowledge/articles/index/reconcile", null, admin);
        assertThat(article(id, admin).get("indexStatus").asText()).isEqualTo("INDEXED");
        assertThat(callsFor(id).get(callsFor(id).size() - 1).action()).isEqualTo(IndexRequest.Action.UPSERT);
    }

    @Test
    void integrationContractRequiresServiceSecretAndReflectsRelationalTruth() throws Exception {
        String admin = adminToken();
        long eligible = publishArticle("Proxy mal configurado", "Restablecer el proxy del sistema");
        long inactive = publishArticle("Artículo retirado", "No aplica");
        request("POST", "/api/knowledge/articles/" + inactive + "/deactivate", null, admin);
        String body = "{\"schemaVersion\":1,\"articles\":[{\"articleId\":" + eligible + ",\"contentVersion\":1},"
                + "{\"articleId\":" + eligible + ",\"contentVersion\":7},{\"articleId\":" + inactive + ",\"contentVersion\":1},"
                + "{\"articleId\":999999999,\"contentVersion\":1}]}";

        // Sin secreto, con secreto incorrecto o con un JWT de administrador: rechazado.
        assertThat(integrationRequest("POST", "/api/integration/knowledge/eligibility", body, null).statusCode()).isEqualTo(401);
        assertThat(integrationRequest("POST", "/api/integration/knowledge/eligibility", body, "incorrecto").statusCode()).isEqualTo(401);
        assertThat(request("POST", "/api/integration/knowledge/eligibility", body, admin).statusCode()).isEqualTo(401);
        // El secreto de servicio no abre el resto de la API.
        assertThat(integrationRequest("GET", "/api/tickets", null, INTEGRATION_TOKEN).statusCode()).isEqualTo(401);
        assertThat(integrationRequest("GET", "/api/user/all", null, INTEGRATION_TOKEN).statusCode()).isEqualTo(401);

        HttpResponse<String> response = integrationRequest("POST", "/api/integration/knowledge/eligibility", body, INTEGRATION_TOKEN);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode items = json(response).get("articles");
        assertThat(items.get(0).get("usable").asBoolean()).isTrue();
        assertThat(items.get(0).get("solution").asText()).isEqualTo("Restablecer el proxy del sistema");
        // Versión no vigente, artículo inactivo o inexistente: no utilizable y sin contenido.
        for (int i = 1; i < 4; i++) {
            assertThat(items.get(i).get("usable").asBoolean()).as("item " + i).isFalse();
            assertThat(items.get(i).get("solution").isNull()).isTrue();
        }
        assertThat(items.get(1).get("currentVersion").asInt()).isEqualTo(1);

        JsonNode list = json(integrationRequest("GET", "/api/integration/knowledge/eligible", null, INTEGRATION_TOKEN));
        List<Long> ids = new java.util.ArrayList<>();
        list.forEach(item -> ids.add(item.get("articleId").asLong()));
        assertThat(ids).contains(eligible).doesNotContain(inactive);
    }

    private void publishArticleQuietly(String title) throws Exception {
        var previous = n8n.onIndex;
        n8n.onIndex = FakeN8nClient::indexed;
        publishArticle(title, "Solución de referencia");
        n8n.onIndex = previous;
    }

    private List<IndexRequest> callsFor(long id) {
        return n8n.indexCalls.stream().filter(call -> call.articleId() == id).toList();
    }

    private JsonNode article(long id, String token) throws Exception {
        return json(request("GET", "/api/knowledge/articles/" + id, null, token));
    }
}

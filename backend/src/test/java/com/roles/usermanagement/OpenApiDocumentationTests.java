package com.roles.usermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El contrato publicado en Swagger incluye los endpoints de conocimiento, asistente, calificación y
 * analítica. La especificación se guarda en build/openapi/openapi.json como evidencia reproducible.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiDocumentationTests extends ApiTestSupport {

    @Test
    void publishedContractDocumentsTheAssistantKnowledgeAndAnalyticsEndpoints() throws Exception {
        // La documentación es pública; la API no.
        HttpResponse<String> response = request("GET", "/v3/api-docs", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode spec = json(response);
        JsonNode paths = spec.get("paths");

        assertThat(paths.get("/api/tickets/{ticketId}/assistance").has("post")).isTrue();
        assertThat(paths.get("/api/tickets/{ticketId}/assistance").has("get")).isTrue();
        assertThat(paths.get("/api/assistance/queries/{queryId}").has("get")).isTrue();
        assertThat(paths.get("/api/assistance/queries/{queryId}/rating").has("put")).isTrue();
        assertThat(paths.get("/api/knowledge/articles").has("post")).isTrue();
        assertThat(paths.get("/api/knowledge/articles/{id}/deactivate").has("post")).isTrue();
        assertThat(paths.get("/api/knowledge/articles/index/reconcile").has("post")).isTrue();
        assertThat(paths.get("/api/analytics/summary").has("get")).isTrue();
        assertThat(paths.get("/api/integration/knowledge/eligibility").has("post")).isTrue();
        // Los errores externos del asistente están documentados con su código HTTP.
        JsonNode ask = paths.get("/api/tickets/{ticketId}/assistance").get("post").get("responses");
        assertThat(ask.has("201")).isTrue();
        assertThat(ask.has("502")).isTrue();
        assertThat(ask.has("503")).isTrue();
        assertThat(ask.has("504")).isTrue();
        assertThat(spec.get("components").get("schemas").has("AssistanceQueryResponse")).isTrue();
        assertThat(spec.get("components").get("schemas").has("RatingRequest")).isTrue();

        Path target = Path.of("build", "openapi", "openapi.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec));
    }
}

package com.roles.usermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.roles.usermanagement.integration.n8n.dto.AssistResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AssistanceTests extends ApiTestSupport {

    @Autowired
    private FakeN8nClient fakeN8nClient;

    private long ticketId;
    private long articleId;
    private String userToken;
    private String admin;

    @BeforeEach
    void setup() throws Exception {
        fakeN8nClient.reset();
        admin = adminToken();

        String username = registerUser();
        userToken = token(username, PASSWORD);

        ticketId = createTicket(userToken);
        articleId = createAndPublishArticle();
    }

    private long createAndPublishArticle() throws Exception {
        String techToken = token(createTechnician(), PASSWORD);
        String body = "{\"title\":\"Solución Conectividad\",\"problem\":\"Sin red\",\"symptoms\":\"DHCP no responde\","
                + "\"cause\":\"Router colgado\",\"solution\":\"Reiniciar router y renovar IP\",\"categoryName\":\"Conectividad\"}";
        HttpResponse<String> res = request("POST", "/api/knowledge/articles", body, techToken);
        assertThat(res.statusCode()).isEqualTo(201);
        long id = json(res).get("id").asLong();

        request("POST", "/api/knowledge/articles/" + id + "/verify", null, admin);
        request("POST", "/api/knowledge/articles/" + id + "/activate", null, admin);
        return id;
    }

    @Test
    void askAssistanceGeneratesAnsweredQueryAndAllowsRating() throws Exception {
        fakeN8nClient.onAssist = req -> FakeN8nClient.answered(
                req,
                "Resumen de solución verificada",
                List.of(new AssistResponse.Source(articleId, 1, 0.95, List.of()))
        );

        HttpResponse<String> askRes = request("POST", "/api/tickets/" + ticketId + "/assistance",
                "{\"prompt\":\"¿Cómo soluciono la red?\"}", userToken);

        assertThat(askRes.statusCode()).isEqualTo(201);
        JsonNode askJson = json(askRes);
        assertThat(askJson.get("status").asText()).isEqualTo("ANSWERED");

        JsonNode responseNode = askJson.get("response");
        assertThat(responseNode.isNull()).isFalse();
        assertThat(responseNode.get("summary").asText()).isEqualTo("Resumen de solución verificada");
        long responseId = responseNode.get("id").asLong();

        HttpResponse<String> rateRes = request("POST", "/api/assistance/responses/" + responseId + "/rate",
                "{\"stars\":5,\"comment\":\"Excelente solución\"}", userToken);

        assertThat(rateRes.statusCode()).isEqualTo(200);
        JsonNode rateJson = json(rateRes);
        assertThat(rateJson.get("stars").asInt()).isEqualTo(5);
        assertThat(rateJson.get("comment").asText()).isEqualTo("Excelente solución");
    }

    @Test
    void askAssistanceReturnsNoResultsWhenNoSourcesMatch() throws Exception {
        fakeN8nClient.onAssist = FakeN8nClient::noResults;

        HttpResponse<String> askRes = request("POST", "/api/tickets/" + ticketId + "/assistance",
                "{\"prompt\":\"Consulta sin fuentes\"}", userToken);

        assertThat(askRes.statusCode()).isEqualTo(201);
        JsonNode askJson = json(askRes);
        assertThat(askJson.get("status").asText()).isEqualTo("NO_RESULTS");
        assertThat(askJson.get("response").isNull()).isTrue();
    }
}

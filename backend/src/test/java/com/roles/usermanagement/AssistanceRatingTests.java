package com.roles.usermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.roles.usermanagement.integration.n8n.dto.AssistResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF08: calificación de la utilidad de una respuesta, de 1 a 5, por quien hizo la consulta. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AssistanceRatingTests extends ApiTestSupport {

    @Autowired
    private FakeN8nClient n8n;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void resetN8n() {
        n8n.reset();
    }

    /** Prueba obligatoria 10: calificar una respuesta y comprobar la persistencia y el rango 1–5. */
    @Test
    void requesterRatesAnAnsweredQueryAndTheRatingIsPersistedAndUpdated() throws Exception {
        String admin = adminToken();
        String user = registerUser();
        String userToken = token(user, PASSWORD);
        String technician = createTechnician();
        String techToken = token(technician, PASSWORD);
        long article = publishArticle("Proyector sin imagen", "Seleccionar la entrada HDMI correcta");
        long ticket = createTicket(userToken);
        assign(ticket, technician, admin);
        long query = answeredQuery(ticket, article, userToken);
        String path = "/api/assistance/queries/" + query + "/rating";

        // Sin calificar todavía.
        assertThat(json(request("GET", "/api/assistance/queries/" + query, null, userToken)).get("rating").isNull()).isTrue();

        HttpResponse<String> created = request("PUT", path, "{\"score\":4,\"comment\":\"  Resolvió el problema  \"}", userToken);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        JsonNode rating = json(created);
        assertThat(rating.get("queryId").asLong()).isEqualTo(query);
        assertThat(rating.get("score").asInt()).isEqualTo(4);
        assertThat(rating.get("comment").asText()).isEqualTo("Resolvió el problema");
        assertThat(rating.get("ratedBy").asText()).isEqualTo(user);
        assertThat(count(query)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select score from assistance_ratings where username = ?", Integer.class, user)).isEqualTo(4);

        // Visible para quien puede ver el incidente, tanto en la consulta como en el historial.
        JsonNode seenByTech = json(request("GET", "/api/assistance/queries/" + query, null, techToken)).get("rating");
        assertThat(seenByTech.get("score").asInt()).isEqualTo(4);
        assertThat(json(request("GET", "/api/tickets/" + ticket + "/assistance", null, admin))
                .get("content").get(0).get("rating").get("comment").asText()).isEqualTo("Resolvió el problema");

        // Calificar de nuevo actualiza la misma calificación: los límites 1 y 5 son válidos y el comentario es opcional.
        JsonNode updated = json(request("PUT", path, "{\"score\":1}", userToken));
        assertThat(updated.get("score").asInt()).isEqualTo(1);
        assertThat(updated.get("comment").isNull()).isTrue();
        assertThat(updated.get("createdAt").asText()).isEqualTo(rating.get("createdAt").asText());
        assertThat(json(request("PUT", path, "{\"score\":5,\"comment\":\"   \"}", userToken)).get("score").asInt()).isEqualTo(5);
        assertThat(count(query)).isEqualTo(1);

        // Fuera de rango, no entera o ausente: 400 y la calificación guardada no cambia.
        for (String invalid : List.of("{\"score\":0}", "{\"score\":6}", "{\"score\":-1}", "{\"score\":4.5}", "{\"score\":null}",
                "{}", "{\"score\":\"cinco\"}", "{\"score\":3,\"comment\":\"" + "a".repeat(1001) + "\"}")) {
            assertThat(request("PUT", path, invalid, userToken).statusCode()).as(invalid).isEqualTo(400);
        }
        assertThat(jdbc.queryForObject("select score from assistance_ratings where username = ?", Integer.class, user)).isEqualTo(5);

        // La calificación no altera el artículo ni la similitud registrada.
        JsonNode articleAfter = json(request("GET", "/api/knowledge/articles/" + article, null, admin));
        assertThat(articleAfter.get("verified").asBoolean()).isTrue();
        assertThat(articleAfter.get("contentVersion").asInt()).isEqualTo(1);
        assertThat(json(request("GET", "/api/assistance/queries/" + query, null, userToken))
                .get("sources").get(0).get("similarity").asDouble()).isEqualTo(0.9);
    }

    @Test
    void onlyTheRequesterOfAnAnsweredQueryCanRate() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        String otherUserToken = token(registerUser(), PASSWORD);
        String technician = createTechnician();
        String techToken = token(technician, PASSWORD);
        long article = publishArticle("Mouse errático", "Limpiar el sensor óptico");
        long ticket = createTicket(userToken);
        assign(ticket, technician, admin);
        long query = answeredQuery(ticket, article, userToken);
        String path = "/api/assistance/queries/" + query + "/rating";
        String body = "{\"score\":5}";

        // Consulta ajena: ni otro usuario, ni el técnico asignado, ni el administrador la califican.
        assertThat(request("PUT", path, body, otherUserToken).statusCode()).isEqualTo(403);
        assertThat(request("PUT", path, body, techToken).statusCode()).isEqualTo(403);
        assertThat(request("PUT", path, body, admin).statusCode()).isEqualTo(403);
        assertThat(request("PUT", path, body, null).statusCode()).isEqualTo(401);
        assertThat(request("PUT", "/api/assistance/queries/999999999/rating", body, userToken).statusCode()).isEqualTo(404);
        assertThat(count(query)).isZero();

        // Cada rol califica las respuestas a sus propias consultas.
        long byTechnician = answeredQuery(ticket, article, techToken);
        assertThat(request("PUT", "/api/assistance/queries/" + byTechnician + "/rating", body, techToken).statusCode()).isEqualTo(200);
        assertThat(request("PUT", "/api/assistance/queries/" + byTechnician + "/rating", body, userToken).statusCode()).isEqualTo(403);

        // Una consulta sin respuesta (sin resultados o con error) no se califica.
        n8n.onAssist = FakeN8nClient::noResults;
        long noResults = json(ask(ticket, userToken)).get("id").asLong();
        assertThat(request("PUT", "/api/assistance/queries/" + noResults + "/rating", body, userToken).statusCode()).isEqualTo(409);
        n8n.onAssist = request -> new AssistResponse(1, request.requestId(), request.queryId(), "ERROR", null, null, null,
                null, null, null, null, null, "EMBEDDING_FAILED");
        long failed = json(ask(ticket, userToken)).get("queryId").asLong();
        assertThat(request("PUT", "/api/assistance/queries/" + failed + "/rating", body, userToken).statusCode()).isEqualTo(409);

        // Un incidente cerrado sigue permitiendo calificar las respuestas anteriores.
        String transitions = "/api/tickets/" + ticket + "/transitions";
        assertThat(request("POST", transitions, "{\"action\":\"START_PROGRESS\"}", techToken).statusCode()).isEqualTo(200);
        assertThat(request("POST", transitions, "{\"action\":\"RESOLVE\",\"resolutionNote\":\"Listo\"}", techToken).statusCode()).isEqualTo(200);
        assertThat(request("POST", transitions, "{\"action\":\"CONFIRM_CLOSE\"}", userToken).statusCode()).isEqualTo(200);
        assertThat(request("PUT", path, "{\"score\":3}", userToken).statusCode()).isEqualTo(200);
        assertThat(count(query)).isEqualTo(1);
    }

    /** El rango y la unicidad también están protegidos en la base de datos, no solo en el DTO. */
    @Test
    void databaseRejectsOutOfRangeOrDuplicateRatings() throws Exception {
        String user = registerUser();
        String userToken = token(user, PASSWORD);
        long article = publishArticle("Audio sin sonido", "Seleccionar el dispositivo de salida");
        long query = answeredQuery(createTicket(userToken), article, userToken);
        Long response = jdbc.queryForObject("select id from assistance_responses where query_id = ?", Long.class, query);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String insert = "insert into assistance_ratings (response_id, username, score, created_at, updated_at) values (?, ?, ?, ?, ?)";

        assertThatThrownBy(() -> jdbc.update(insert, response, user, 6, now, now)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, response, user, 0, now, now)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insert, response, user, 5, now, now);
        assertThatThrownBy(() -> jdbc.update(insert, response, user, 4, now, now)).isInstanceOf(DataIntegrityViolationException.class);
        // Una segunda respuesta para la misma consulta tampoco es posible.
        assertThatThrownBy(() -> jdbc.update("insert into assistance_responses (query_id, answer, created_at) values (?, ?, ?)",
                query, "Otra respuesta", now)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private long answeredQuery(long ticket, long article, String token) throws Exception {
        n8n.onAssist = request -> FakeN8nClient.answered(request, "Respuesta de prueba",
                List.of(new AssistResponse.Source(article, 1, 0.9, null)));
        HttpResponse<String> response = ask(ticket, token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json(response).get("id").asLong();
    }

    private HttpResponse<String> ask(long ticket, String token) throws Exception {
        return request("POST", "/api/tickets/" + ticket + "/assistance", null, token);
    }

    private void assign(long ticket, String technician, String admin) throws Exception {
        assertThat(request("PATCH", "/api/tickets/" + ticket + "/assign",
                "{\"technicianUsername\":\"" + technician + "\"}", admin).statusCode()).isEqualTo(200);
    }

    private long count(long query) {
        return jdbc.queryForObject("select count(*) from assistance_ratings r join assistance_responses a on a.id = r.response_id "
                + "where a.query_id = ?", Long.class, query);
    }
}

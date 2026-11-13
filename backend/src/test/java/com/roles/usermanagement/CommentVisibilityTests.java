package com.roles.usermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** §6.3: el solicitante solo ve comentarios públicos; las notas internas son de ADMIN y del técnico asignado. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CommentVisibilityTests extends ApiTestSupport {

    @Test
    void applicantNeverSeesInternalNotes() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        String otherUserToken = token(registerUser(), PASSWORD);
        String technician = createTechnician();
        String techToken = token(technician, PASSWORD);
        long ticket = createTicket(userToken);
        assertThat(request("PATCH", "/api/tickets/" + ticket + "/assign",
                "{\"technicianUsername\":\"" + technician + "\"}", admin).statusCode()).isEqualTo(200);
        String path = "/api/tickets/" + ticket + "/comments";

        assertThat(request("POST", path, "{\"content\":\"Nota interna del técnico\",\"visibility\":\"INTERNAL\"}", techToken)
                .statusCode()).isEqualTo(201);
        assertThat(request("POST", path, "{\"content\":\"Nota interna del administrador\",\"visibility\":\"INTERNAL\"}", admin)
                .statusCode()).isEqualTo(201);
        assertThat(request("POST", path, "{\"content\":\"Estamos revisando su caso\"}", techToken).statusCode()).isEqualTo(201);
        // El solicitante no puede crear notas internas; su comentario es público.
        assertThat(request("POST", path, "{\"content\":\"Intento\",\"visibility\":\"INTERNAL\"}", userToken).statusCode()).isEqualTo(403);
        assertThat(request("POST", path, "{\"content\":\"Gracias\"}", userToken).statusCode()).isEqualTo(201);

        assertThat(contents(path, userToken)).containsExactly("Estamos revisando su caso", "Gracias");
        assertThat(request("GET", path, null, userToken).body()).doesNotContain("Nota interna");
        assertThat(contents(path, techToken)).hasSize(4).contains("Nota interna del técnico", "Nota interna del administrador");
        assertThat(contents(path, admin)).hasSize(4);
        // Un tercero no ve ningún comentario del incidente.
        assertThat(request("GET", path, null, otherUserToken).statusCode()).isEqualTo(403);
    }

    private List<String> contents(String path, String token) throws Exception {
        JsonNode comments = json(request("GET", path, null, token));
        List<String> result = new ArrayList<>();
        comments.forEach(comment -> result.add(comment.get("content").asText()));
        return result;
    }
}

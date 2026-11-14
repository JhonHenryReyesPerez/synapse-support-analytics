package com.roles.usermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** B08 y §6: fechas de resolución/cierre, nota obligatoria, máquina de estados y validaciones de creación. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TicketResolutionTests extends ApiTestSupport {

    @Test
    void resolveRequiresNoteAndSetsResolvedAtWithoutLaterChanges() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        String technician = createTechnician();
        String techToken = token(technician, PASSWORD);
        long ticket = createTicket(userToken);
        assign(ticket, technician, admin);

        // No se puede saltar IN_PROGRESS.
        assertThat(transition(ticket, "{\"action\":\"RESOLVE\",\"resolutionNote\":\"x\"}", techToken).statusCode()).isEqualTo(409);
        assertThat(transition(ticket, "{\"action\":\"START_PROGRESS\"}", techToken).statusCode()).isEqualTo(200);

        // Sin nota (o solo espacios) no se resuelve.
        assertThat(transition(ticket, "{\"action\":\"RESOLVE\"}", techToken).statusCode()).isEqualTo(400);
        assertThat(transition(ticket, "{\"action\":\"RESOLVE\",\"resolutionNote\":\"   \"}", techToken).statusCode()).isEqualTo(400);
        assertThat(ticket(ticket, admin).get("resolvedAt").isNull()).isTrue();

        assertThat(transition(ticket, "{\"action\":\"RESOLVE\",\"resolutionNote\":\" Se renovó la concesión DHCP \"}", techToken)
                .statusCode()).isEqualTo(200);
        JsonNode resolved = ticket(ticket, admin);
        assertThat(resolved.get("status").asText()).isEqualTo("RESOLVED");
        assertThat(resolved.get("resolutionNote").asText()).isEqualTo("Se renovó la concesión DHCP");
        assertThat(resolved.get("closedAt").isNull()).isTrue();
        String resolvedAt = resolved.get("resolvedAt").asText();
        assertThat(LocalDateTime.parse(resolvedAt)).isAfterOrEqualTo(LocalDateTime.parse(resolved.get("createdAt").asText()));

        // El cierre fija closedAt y conserva resolvedAt.
        assertThat(transition(ticket, "{\"action\":\"CONFIRM_CLOSE\"}", userToken).statusCode()).isEqualTo(200);
        JsonNode closed = ticket(ticket, admin);
        assertThat(closed.get("resolvedAt").asText()).isEqualTo(resolvedAt);
        assertThat(LocalDateTime.parse(closed.get("closedAt").asText())).isAfterOrEqualTo(LocalDateTime.parse(resolvedAt));

        // Sin reapertura: un ticket cerrado no admite más transiciones.
        assertThat(transition(ticket, "{\"action\":\"START_PROGRESS\"}", techToken).statusCode()).isEqualTo(409);
    }

    @Test
    void onlyAssignedTechnicianAndApplicantDriveTheLifecycle() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        String otherUserToken = token(registerUser(), PASSWORD);
        String technician = createTechnician();
        String otherTechToken = token(createTechnician(), PASSWORD);
        String techToken = token(technician, PASSWORD);
        long ticket = createTicket(userToken);
        assign(ticket, technician, admin);

        // Otro técnico no ve ni opera el ticket; el administrador no inicia la atención.
        assertThat(transition(ticket, "{\"action\":\"START_PROGRESS\"}", otherTechToken).statusCode()).isEqualTo(403);
        assertThat(transition(ticket, "{\"action\":\"START_PROGRESS\"}", admin).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/tickets/" + ticket, null, otherTechToken).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/tickets/" + ticket + "/history", null, otherUserToken).statusCode()).isEqualTo(403);

        assertThat(transition(ticket, "{\"action\":\"START_PROGRESS\"}", techToken).statusCode()).isEqualTo(200);
        assertThat(transition(ticket, "{\"action\":\"RESOLVE\",\"resolutionNote\":\"Listo\"}", techToken).statusCode()).isEqualTo(200);

        // Solo el solicitante confirma el cierre.
        assertThat(transition(ticket, "{\"action\":\"CONFIRM_CLOSE\"}", techToken).statusCode()).isEqualTo(403);
        assertThat(transition(ticket, "{\"action\":\"CONFIRM_CLOSE\"}", otherUserToken).statusCode()).isEqualTo(403);
        assertThat(transition(ticket, "{\"action\":\"CONFIRM_CLOSE\"}", userToken).statusCode()).isEqualTo(200);
        assertThat(request("GET", "/api/tickets/999999999", null, admin).statusCode()).isEqualTo(404);
    }

    @Test
    void creationValidatesTextAndIgnoresPrivilegedFields() throws Exception {
        String userToken = token(registerUser(), PASSWORD);
        String technician = createTechnician();
        String base = "\"category\":\"Software\",\"priority\":\"MEDIA\"";

        assertThat(create("{\"title\":\"   \",\"description\":\"Algo falla\"," + base + "}", userToken).statusCode()).isEqualTo(400);
        assertThat(create("{\"title\":\"Fallo\",\"description\":\"   \"," + base + "}", userToken).statusCode()).isEqualTo(400);
        assertThat(create("{\"title\":\"" + "a".repeat(151) + "\",\"description\":\"Algo falla\"," + base + "}", userToken)
                .statusCode()).isEqualTo(400);
        assertThat(create("{\"title\":\"Fallo\",\"description\":\"Algo falla\",\"category\":\"Software\"}", userToken)
                .statusCode()).isEqualTo(400);

        // Título corto válido tras trim; estado, técnico, solicitante y fechas enviados por el cliente se ignoran.
        HttpResponse<String> created = create("{\"title\":\"  PC  \",\"description\":\"No enciende\"," + base
                + ",\"status\":\"CLOSED\",\"technicianUsername\":\"" + technician + "\",\"applicantUsername\":\"" + ADMIN
                + "\",\"createdAt\":\"2020-01-01T00:00:00\",\"resolvedAt\":\"2020-01-02T00:00:00\"}", userToken);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        JsonNode ticket = json(created);
        assertThat(ticket.get("title").asText()).isEqualTo("PC");
        assertThat(ticket.get("status").asText()).isEqualTo("OPEN");
        assertThat(ticket.get("technicianUsername").isNull()).isTrue();
        assertThat(ticket.get("applicantUsername").asText()).isNotEqualTo(ADMIN);
        assertThat(ticket.get("createdAt").asText()).doesNotStartWith("2020");
        assertThat(ticket.get("resolvedAt").isNull()).isTrue();

        // Solo USER registra incidentes.
        assertThat(create("{\"title\":\"Fallo\",\"description\":\"Algo falla\"," + base + "}", adminToken()).statusCode()).isEqualTo(403);
        assertThat(create("{\"title\":\"Fallo\",\"description\":\"Algo falla\"," + base + "}", token(technician, PASSWORD))
                .statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> create(String body, String token) throws Exception {
        return request("POST", "/api/tickets", body, token);
    }

    private void assign(long ticket, String technician, String admin) throws Exception {
        assertThat(request("PATCH", "/api/tickets/" + ticket + "/assign",
                "{\"technicianUsername\":\"" + technician + "\"}", admin).statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> transition(long ticket, String body, String token) throws Exception {
        return request("POST", "/api/tickets/" + ticket + "/transitions", body, token);
    }

    private JsonNode ticket(long ticket, String token) throws Exception {
        return json(request("GET", "/api/tickets/" + ticket, null, token));
    }
}

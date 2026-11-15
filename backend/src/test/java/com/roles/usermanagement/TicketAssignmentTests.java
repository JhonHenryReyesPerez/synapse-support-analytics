package com.roles.usermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.roles.usermanagement.modules.assignment.entity.TicketAssignmentLog;
import com.roles.usermanagement.modules.assignment.enums.AssignmentType;
import com.roles.usermanagement.modules.assignment.repository.TicketAssignmentLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

/** B05: una sola implementación de asignación para todas las rutas, con auditoría idéntica. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TicketAssignmentTests extends ApiTestSupport {

    @Autowired
    private TicketAssignmentLogRepository assignmentLogs;

    @Test
    void assignRoutesProduceSameStateHistoryAndAudit() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        String technician = createTechnician();
        long viaAssign = createTicket(userToken);
        long viaTransition = createTicket(userToken);

        HttpResponse<String> first = request("PATCH", "/api/tickets/" + viaAssign + "/assign",
                "{\"technicianUsername\":\"" + technician + "\"}", admin);
        HttpResponse<String> second = request("POST", "/api/tickets/" + viaTransition + "/transitions",
                "{\"action\":\"ASSIGN\",\"technicianUsername\":\"" + technician + "\"}", admin);

        assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
        assertThat(second.statusCode()).as(second.body()).isEqualTo(200);
        assertThat(json(first).get("status").asText()).isEqualTo("ASSIGNED");
        assertThat(json(second).get("newStatus").asText()).isEqualTo("ASSIGNED");

        assertThat(historySummary(viaAssign, admin)).isEqualTo(historySummary(viaTransition, admin));
        assertThat(auditSummary(viaAssign)).isEqualTo(auditSummary(viaTransition))
                .containsExactly("INITIAL|null|" + technician + "|" + ADMIN + "|null");
    }

    @Test
    void reassignRoutesProduceSameAuditAndRequireJustification() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        String firstTech = createTechnician();
        String secondTech = createTechnician();
        long viaReassign = assignedTicket(userToken, firstTech, admin);
        long viaTransition = assignedTicket(userToken, firstTech, admin);

        HttpResponse<String> missing = request("POST", "/api/tickets/" + viaTransition + "/transitions",
                "{\"action\":\"REASSIGN\",\"technicianUsername\":\"" + secondTech + "\"}", admin);
        assertThat(missing.statusCode()).isEqualTo(400);

        String reassignBody = "\"technicianUsername\":\"" + secondTech + "\",\"justification\":\"Especialidad en redes\"}";
        assertThat(request("PUT", "/api/tickets/" + viaReassign + "/reassign", "{" + reassignBody, admin)
                .statusCode()).isEqualTo(200);
        assertThat(request("POST", "/api/tickets/" + viaTransition + "/transitions",
                "{\"action\":\"REASSIGN\"," + reassignBody, admin).statusCode()).isEqualTo(200);

        assertThat(historySummary(viaReassign, admin)).isEqualTo(historySummary(viaTransition, admin));
        assertThat(auditSummary(viaReassign)).isEqualTo(auditSummary(viaTransition))
                .hasSize(2)
                .last().isEqualTo("REASSIGNMENT|" + firstTech + "|" + secondTech + "|" + ADMIN + "|Especialidad en redes");
    }

    @Test
    void rejectsInvalidTargetsOnBothRoutes() throws Exception {
        String admin = adminToken();
        String plainUser = registerUser();
        String userToken = token(plainUser, PASSWORD);
        String technician = createTechnician();
        long ticket = createTicket(userToken);

        // Un usuario sin rol TECHNICIAN no es asignable por ninguna ruta.
        assertThat(request("PATCH", "/api/tickets/" + ticket + "/assign",
                "{\"technicianUsername\":\"" + plainUser + "\"}", admin).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/api/tickets/" + ticket + "/transitions",
                "{\"action\":\"ASSIGN\",\"technicianUsername\":\"" + plainUser + "\"}", admin).statusCode()).isEqualTo(400);

        // Un técnico deshabilitado tampoco.
        String disabled = createTechnician();
        assertThat(request("PUT", "/api/user/update", "{\"username\":\"" + disabled + "\",\"disabled\":true}", admin)
                .statusCode()).isEqualTo(200);
        assertThat(request("PATCH", "/api/tickets/" + ticket + "/assign",
                "{\"technicianUsername\":\"" + disabled + "\"}", admin).statusCode()).isEqualTo(400);

        // Solo ADMIN asigna; un ticket ya asignado no admite una segunda asignación inicial.
        assertThat(request("POST", "/api/tickets/" + ticket + "/transitions",
                "{\"action\":\"ASSIGN\",\"technicianUsername\":\"" + technician + "\"}", userToken).statusCode()).isEqualTo(403);
        assertThat(request("PATCH", "/api/tickets/" + ticket + "/assign",
                "{\"technicianUsername\":\"" + technician + "\"}", admin).statusCode()).isEqualTo(200);
        assertThat(request("PATCH", "/api/tickets/" + ticket + "/assign",
                "{\"technicianUsername\":\"" + technician + "\"}", admin).statusCode()).isEqualTo(409);
        assertThat(auditSummary(ticket)).hasSize(1);
    }

    @Test
    void concurrentAssignmentsLeaveOneConsistentResult() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        String firstTech = createTechnician();
        String secondTech = createTechnician();
        long ticket = createTicket(userToken);
        CountDownLatch start = new CountDownLatch(1);

        var first = CompletableFuture.supplyAsync(() -> assignWhenReleased(start, ticket, firstTech, admin));
        var second = CompletableFuture.supplyAsync(() -> assignWhenReleased(start, ticket, secondTech, admin));
        start.countDown();

        assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
        List<String> audit = auditSummary(ticket);
        assertThat(audit).hasSize(1);
        JsonNode stored = json(request("GET", "/api/tickets/" + ticket, null, admin));
        assertThat(audit.get(0)).contains("|" + stored.get("technicianUsername").asText() + "|");
    }

    @Test
    void assignedTicketCannotBeEditedByItsApplicant() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        String technician = createTechnician();
        long ticket = assignedTicket(userToken, technician, admin);

        assertThat(request("PUT", "/api/tickets/" + ticket, EDIT_BODY, userToken).statusCode()).isEqualTo(409);

        JsonNode stored = json(request("GET", "/api/tickets/" + ticket, null, admin));
        assertThat(stored.get("status").asText()).isEqualTo("ASSIGNED");
        assertThat(stored.get("technicianUsername").asText()).isEqualTo(technician);
        assertThat(stored.get("title").asText()).isEqualTo("Sin conexión a la red");
    }

    /** Una edición simultánea no debe devolver a OPEN un ticket que el administrador acaba de asignar. */
    @Test
    void editRacingWithAssignmentNeverUndoesIt() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        String technician = createTechnician();

        for (int round = 0; round < 8; round++) {
            long ticket = createTicket(userToken);
            CountDownLatch start = new CountDownLatch(1);
            var edit = CompletableFuture.supplyAsync(() -> editWhenReleased(start, ticket, userToken));
            var assign = CompletableFuture.supplyAsync(() -> assignWhenReleased(start, ticket, technician, admin));
            start.countDown();

            assertThat(assign.get()).isEqualTo(200);
            assertThat(edit.get()).isIn(200, 409);
            JsonNode stored = json(request("GET", "/api/tickets/" + ticket, null, admin));
            assertThat(stored.get("status").asText()).isEqualTo("ASSIGNED");
            assertThat(stored.get("technicianUsername").asText()).isEqualTo(technician);
        }
    }

    @Test
    void technicianListingDerivesLoadFromActiveTickets() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        String technician = createTechnician();

        assertThat(loadOf(technician, admin)).isZero();
        assignedTicket(userToken, technician, admin);
        assignedTicket(userToken, technician, admin);
        assertThat(loadOf(technician, admin)).isEqualTo(2);
    }

    private static final String EDIT_BODY = "{\"title\":\"Título editado\",\"description\":\"Descripción editada\","
            + "\"category\":\"NETWORK\",\"priority\":\"HIGH\"}";

    private int editWhenReleased(CountDownLatch start, long ticket, String userToken) {
        try {
            start.await();
            return request("PUT", "/api/tickets/" + ticket, EDIT_BODY, userToken).statusCode();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int assignWhenReleased(CountDownLatch start, long ticket, String technician, String admin) {
        try {
            start.await();
            return request("PATCH", "/api/tickets/" + ticket + "/assign",
                    "{\"technicianUsername\":\"" + technician + "\"}", admin).statusCode();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private long assignedTicket(String userToken, String technician, String admin) throws Exception {
        long ticket = createTicket(userToken);
        assertThat(request("PATCH", "/api/tickets/" + ticket + "/assign",
                "{\"technicianUsername\":\"" + technician + "\"}", admin).statusCode()).isEqualTo(200);
        return ticket;
    }

    private int loadOf(String technician, String admin) throws Exception {
        for (JsonNode item : json(request("GET", "/api/technicians", null, admin))) {
            if (technician.equals(item.get("username").asText())) {
                return item.get("activeTicketsCount").asInt();
            }
        }
        throw new AssertionError("El técnico " + technician + " no aparece en el listado");
    }

    /** Historial sin IDs ni fechas: previousStatus>newStatus:action:changedBy por evento. */
    private List<String> historySummary(long ticket, String admin) throws Exception {
        HttpResponse<String> response = request("GET", "/api/tickets/" + ticket + "/history", null, admin);
        assertThat(response.statusCode()).isEqualTo(200);
        List<String> result = new java.util.ArrayList<>();
        for (JsonNode item : json(response)) {
            JsonNode copy = item.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) copy).remove(List.of("id", "ticketId", "changedAt"));
            result.add(copy.toString());
        }
        return result;
    }

    private List<String> auditSummary(long ticket) {
        return assignmentLogs.findAll().stream()
                .filter(log -> log.getTicket().getId() == ticket)
                .sorted(java.util.Comparator.comparing(TicketAssignmentLog::getId))
                .map(log -> log.getAssignmentType() + "|" + username(log.getPreviousTechnician()) + "|"
                        + username(log.getNewTechnician()) + "|" + username(log.getAssignedBy()) + "|" + log.getJustification())
                .toList();
    }

    private static String username(com.roles.usermanagement.persistance.entity.UserEntity user) {
        return user == null ? null : user.getUsername();
    }
}

package com.roles.usermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * B09 (RF06, RF07): los siete indicadores contrastados con valores calculados a mano y con SQL
 * independiente. Cada prueba aísla su cohorte con una categoría o un usuario propios; las fechas
 * históricas se fijan por SQL porque la API protege createdAt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AnalyticsTests extends ApiTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void indicatorsMatchManualCalculationAndIndependentSql() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        long category = newCategory(admin);
        // Lunes 2026-03-02: dos resueltos (2 h y 4 h); domingo 08: abierto a las 23:30;
        // lunes 09 00:00: resuelto sin fecha (dato incompleto); viernes 20: asignado.
        long closed = ticket(userToken, category, "ALTA", "2026-03-02T08:00:00", "CLOSED", "2026-03-02T10:00:00");
        ticket(userToken, category, "ALTA", "2026-03-02T10:00:00", "RESOLVED", "2026-03-02T14:00:00");
        ticket(userToken, category, "BAJA", "2026-03-08T23:30:00", "OPEN", null);
        ticket(userToken, category, "CRITICA", "2026-03-09T00:00:00", "RESOLVED", null);
        ticket(userToken, category, "BAJA", "2026-03-20T09:00:00", "ASSIGNED", null);
        // Una edición posterior no altera la duración: updated_at no interviene en el cálculo.
        jdbc.update("update tickets set updated_at = ? where id = ?", Timestamp.valueOf("2026-06-01 00:00:00"), closed);

        JsonNode weekly = summary("?categoryId=" + category + "&from=2026-03-02&to=2026-03-22&granularity=WEEK", admin);

        // 1. Total, contrastado con SQL independiente.
        assertThat(weekly.get("totalIncidents").asLong()).isEqualTo(5)
                .isEqualTo(sqlCount("category_id = ?", category));
        // 2 y 6. Por categoría y categoría más recurrente.
        assertThat(weekly.get("byCategory")).hasSize(1);
        assertThat(weekly.get("byCategory").get(0).get("count").asLong()).isEqualTo(5);
        assertThat(weekly.get("byCategory").get(0).get("percentage").asDouble()).isEqualTo(100.0);
        assertThat(weekly.get("mostRecurrentCategories")).hasSize(1);
        assertThat(weekly.get("mostRecurrentCategories").get(0).get("categoryId").asLong()).isEqualTo(category);
        // 3. Evolución semanal (lunes): el domingo 08 pertenece a la semana del 02 y el lunes 09 a la siguiente.
        assertThat(points(weekly)).containsExactly("2026-03-02=3", "2026-03-09=1", "2026-03-16=1");
        assertThat(sqlCount("category_id = ? and created_at >= '2026-03-02' and created_at < '2026-03-09'", category)).isEqualTo(3);
        // 4. Tasa de resolución: RESOLVED y CLOSED cuentan como resueltos (3 de 5).
        assertThat(weekly.get("resolution").get("resolvedCount").asLong()).isEqualTo(3)
                .isEqualTo(sqlCount("category_id = ? and status in ('RESOLVED','CLOSED')", category));
        assertThat(weekly.get("resolution").get("ratePercentage").asDouble()).isCloseTo(60.0, within(1e-9));
        // 5. Tiempo promedio: (2 h + 4 h) / 2; el resuelto sin fecha se excluye y se reporta.
        JsonNode time = weekly.get("resolutionTime");
        assertThat(time.get("unit").asText()).isEqualTo("HOURS");
        assertThat(time.get("averageHours").asDouble()).isCloseTo(3.0, within(1e-6));
        assertThat(time.get("sampleSize").asLong()).isEqualTo(2);
        assertThat(time.get("excludedCount").asLong()).isEqualTo(1);
        assertThat(time.get("byCategory").get(0).get("averageHours").asDouble()).isCloseTo(3.0, within(1e-6));
        // 7. Por prioridad: siempre las cuatro, con ceros.
        assertThat(priorities(weekly)).containsExactly("BAJA=2", "MEDIA=0", "ALTA=2", "CRITICA=1");
        assertThat(weekly.get("byPriority").get(0).get("percentage").asDouble()).isCloseTo(40.0, within(1e-9));

        // Serie diaria con días vacíos en cero.
        JsonNode daily = summary("?categoryId=" + category + "&from=2026-03-02&to=2026-03-22&granularity=DAY", admin);
        List<String> days = points(daily);
        assertThat(days).hasSize(21).contains("2026-03-02=2", "2026-03-03=0", "2026-03-08=1", "2026-03-09=1", "2026-03-20=1");

        // Límites: inicio inclusivo (00:00 entra) y el día "to" completo (23:30 entra).
        assertThat(summary("?categoryId=" + category + "&from=2026-03-09&to=2026-03-09", admin).get("totalIncidents").asLong()).isEqualTo(1);
        assertThat(summary("?categoryId=" + category + "&from=2026-03-08&to=2026-03-08", admin).get("totalIncidents").asLong()).isEqualTo(1);
        // Filtro de prioridad compartido por todos los indicadores.
        JsonNode high = summary("?categoryId=" + category + "&priority=ALTA", admin);
        assertThat(high.get("totalIncidents").asLong()).isEqualTo(2);
        assertThat(high.get("resolution").get("ratePercentage").asDouble()).isEqualTo(100.0);
        assertThat(high.get("resolutionTime").get("excludedCount").asLong()).isZero();
    }

    @Test
    void emptyCohortReturnsZerosAndNullAverage() throws Exception {
        String admin = adminToken();
        long category = newCategory(admin);

        JsonNode empty = summary("?categoryId=" + category, admin);

        assertThat(empty.get("totalIncidents").asLong()).isZero();
        assertThat(empty.get("byCategory")).isEmpty();
        assertThat(empty.get("mostRecurrentCategories")).isEmpty();
        assertThat(empty.get("trend").get("points")).isEmpty();
        assertThat(empty.get("resolution").get("ratePercentage").asDouble()).isZero();
        assertThat(empty.get("resolutionTime").get("averageHours").isNull()).isTrue();
        assertThat(empty.get("resolutionTime").get("sampleSize").asLong()).isZero();
        assertThat(priorities(empty)).containsExactly("BAJA=0", "MEDIA=0", "ALTA=0", "CRITICA=0");
        // Con rango explícito la serie se rellena con ceros.
        assertThat(points(summary("?categoryId=" + category + "&from=2026-01-05&to=2026-01-18", admin)))
                .containsExactly("2026-01-05=0", "2026-01-12=0");
    }

    @Test
    void scopeIsAppliedBeforeAggregatingAndTiesAreReported() throws Exception {
        String admin = adminToken();
        String user = registerUser();
        String userToken = token(user, PASSWORD);
        String technician = createTechnician();
        long first = newCategory(admin);
        long second = newCategory(admin);
        long assigned = ticket(userToken, first, "MEDIA", "2026-04-01T09:00:00", "OPEN", null);
        ticket(userToken, second, "MEDIA", "2026-04-02T09:00:00", "OPEN", null);
        createTicket(token(registerUser(), PASSWORD));
        assertThat(request("PATCH", "/api/tickets/" + assigned + "/assign",
                "{\"technicianUsername\":\"" + technician + "\"}", admin).statusCode()).isEqualTo(200);

        // USER: solo sus dos incidentes, con empate entre categorías.
        JsonNode own = summary("", userToken);
        assertThat(own.get("cohort").get("scope").asText()).isEqualTo("OWN");
        assertThat(own.get("totalIncidents").asLong()).isEqualTo(2)
                .isEqualTo(sqlCount("applicant_username = ?", user));
        assertThat(own.get("mostRecurrentCategories")).hasSize(2);
        assertThat(own.get("byCategory").get(0).get("percentage").asDouble()).isEqualTo(50.0);

        // TECHNICIAN: solo los asignados.
        JsonNode assignedScope = summary("", token(technician, PASSWORD));
        assertThat(assignedScope.get("cohort").get("scope").asText()).isEqualTo("ASSIGNED");
        assertThat(assignedScope.get("totalIncidents").asLong()).isEqualTo(1);

        // ADMIN: global, igual al conteo directo de la tabla.
        JsonNode global = summary("", admin);
        assertThat(global.get("cohort").get("scope").asText()).isEqualTo("GLOBAL");
        assertThat(global.get("totalIncidents").asLong()).isEqualTo(sqlCount("1 = 1"));
        long byCategoryTotal = 0;
        for (JsonNode item : global.get("byCategory")) {
            byCategoryTotal += item.get("count").asLong();
        }
        assertThat(byCategoryTotal).isEqualTo(global.get("totalIncidents").asLong());
    }

    @Test
    void rejectsInvalidFiltersAndAnonymousAccess() throws Exception {
        String admin = adminToken();

        assertThat(request("GET", "/api/analytics/summary?from=2026-03-10&to=2026-03-01", null, admin).statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/analytics/summary?from=ayer", null, admin).statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/analytics/summary?categoryId=999999999", null, admin).statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/analytics/summary?priority=URGENTE", null, admin).statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/analytics/summary?from=2000-01-01&to=2026-01-01&granularity=DAY", null, admin)
                .statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/analytics/summary", null, null).statusCode()).isEqualTo(401);
    }

    private JsonNode summary(String query, String token) throws Exception {
        HttpResponse<String> response = request("GET", "/api/analytics/summary" + query, null, token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json(response);
    }

    private long newCategory(String admin) throws Exception {
        HttpResponse<String> response = request("POST", "/api/categories",
                "{\"name\":\"Analítica " + UUID.randomUUID().toString().substring(0, 8) + "\"}", admin);
        assertThat(response.statusCode()).isEqualTo(201);
        return json(response).get("id").asLong();
    }

    /** Crea el ticket por la API y fija por SQL su fecha de creación, estado y fecha de resolución. */
    private long ticket(String userToken, long category, String priority, String createdAt, String status, String resolvedAt)
            throws Exception {
        HttpResponse<String> response = request("POST", "/api/tickets",
                "{\"title\":\"Caso analítico\",\"description\":\"Dato de prueba\",\"categoryId\":" + category
                        + ",\"priority\":\"" + priority + "\"}", userToken);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        long id = json(response).get("id").asLong();
        jdbc.update("update tickets set created_at = ?, status = ?, resolved_at = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.parse(createdAt)), status,
                resolvedAt == null ? null : Timestamp.valueOf(LocalDateTime.parse(resolvedAt)), id);
        return id;
    }

    private long sqlCount(String condition, Object... args) {
        Long result = jdbc.queryForObject("select count(*) from tickets where " + condition, Long.class, args);
        return result == null ? 0 : result;
    }

    private static List<String> points(JsonNode summary) {
        List<String> result = new ArrayList<>();
        summary.get("trend").get("points").forEach(point ->
                result.add(point.get("periodStart").asText() + "=" + point.get("count").asLong()));
        return result;
    }

    private static List<String> priorities(JsonNode summary) {
        List<String> result = new ArrayList<>();
        summary.get("byPriority").forEach(item -> result.add(item.get("priority").asText() + "=" + item.get("count").asLong()));
        return result;
    }
}

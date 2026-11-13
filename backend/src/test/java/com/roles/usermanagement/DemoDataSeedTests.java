package com.roles.usermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Datos iniciales (§16): carga db/seed/demo_data.sql sobre un PostgreSQL recién migrado y comprueba
 * los mínimos del enunciado, la coherencia de fechas e historial y que la analítica de la API
 * coincide con SQL independiente sobre el conjunto completo. Solo se ejecuta con
 * backend/scripts/test-postgres.sh, que crea una base aparte (TEST_SEED_DB_URL) para no mezclar
 * estos datos con los de las demás pruebas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=${TEST_SEED_DB_URL}")
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TEST_SEED_DB_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoDataSeedTests extends ApiTestSupport {

    private static final String DEMO_PASSWORD = "Demo.2026";
    private static final String COHORT = "from tickets t where t.applicant_username in "
            + "(select username from \"user\" where email like '%@demo.campusdesk.local')";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FakeN8nClient n8n;

    @BeforeAll
    void loadDemoData() throws Exception {
        n8n.reset();
        runScript();
    }

    private void runScript() throws Exception {
        String script = new ClassPathResource("db/seed/demo_data.sql").getContentAsString(StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try {
                statement.execute(script);
            } catch (SQLException e) {
                // El script abre su propia transacción: si falla, se cierra antes de devolver la conexión.
                statement.execute("ROLLBACK");
                throw e;
            }
        }
    }

    @Test
    void seedMeetsTheMandatoryMinimums() {
        assertThat(count("select count(*) " + COHORT)).isEqualTo(180).isGreaterThanOrEqualTo(150);
        assertThat(count("select count(*) from knowledge_articles")).isEqualTo(31);
        assertThat(count("select count(*) from knowledge_articles where active and verified")).isEqualTo(28).isGreaterThanOrEqualTo(25);
        assertThat(count("select count(*) from categories where active")).isGreaterThanOrEqualTo(5);
        assertThat(count("select count(*) from \"user\"")).isGreaterThanOrEqualTo(10);
        assertThat(count("select count(distinct t.status) " + COHORT)).isEqualTo(5);
        assertThat(count("select count(distinct t.priority) " + COHORT)).isEqualTo(4);
        assertThat(count("select count(distinct t.category_id) " + COHORT)).isGreaterThanOrEqualTo(5);
        // Varias fechas y responsables: más de 20 semanas con incidentes, 8 solicitantes y 4 técnicos.
        assertThat(count("select count(distinct date_trunc('week', t.created_at)) " + COHORT)).isGreaterThanOrEqualTo(20);
        assertThat(count("select count(distinct t.applicant_username) " + COHORT)).isEqualTo(8);
        assertThat(count("select count(distinct t.technician_username) " + COHORT)).isEqualTo(4);
        // Cada rol tiene exactamente los usuarios previstos y todos los técnicos tienen perfil.
        assertThat(count("select count(*) from user_role where role = 'TECHNICIAN' and username like 'tec.%'")).isEqualTo(4);
        assertThat(count("select count(*) from technician_profiles where username like 'tec.%'")).isEqualTo(4);
    }

    @Test
    void datesHistoryAndAssignmentsAreCoherent() {
        // creación ≤ asignación ≤ inicio ≤ resolución ≤ cierre, según los eventos del historial.
        assertThat(count("""
                select count(*) from ticket_status_history a
                join ticket_status_history b on b.ticket_id = a.ticket_id and b.id <> a.id
                where a.new_status = b.previous_status and a.new_status <> 'ASSIGNED' and b.changed_at < a.changed_at
                """)).isZero();
        assertThat(count("select count(*) " + COHORT + " and (t.status in ('RESOLVED','CLOSED')) <> (t.resolved_at is not null)")).isZero();
        assertThat(count("select count(*) " + COHORT + " and (t.status = 'CLOSED') <> (t.closed_at is not null)")).isZero();
        assertThat(count("select count(*) " + COHORT + " and (t.resolved_at < t.created_at or t.closed_at < t.resolved_at)")).isZero();
        assertThat(count("select count(*) " + COHORT + " and (t.status = 'OPEN') <> (t.technician_username is null)")).isZero();
        // resolved_at y closed_at coinciden con el evento correspondiente del historial.
        assertThat(count("select count(*) " + COHORT + " and t.resolved_at is distinct from (select min(h.changed_at) "
                + "from ticket_status_history h where h.ticket_id = t.id and h.new_status = 'RESOLVED')")).isZero();
        assertThat(count("select count(*) " + COHORT + " and t.closed_at is distinct from (select min(h.changed_at) "
                + "from ticket_status_history h where h.ticket_id = t.id and h.new_status = 'CLOSED')")).isZero();
        // Cada incidente tiene su evento de creación y el último evento coincide con su estado.
        assertThat(count("select count(*) from ticket_status_history where action_type = 'CREATION'")).isEqualTo(180);
        assertThat(count("select count(*) " + COHORT + " and t.status <> (select h.new_status from ticket_status_history h "
                + "where h.ticket_id = t.id order by h.changed_at desc, h.id desc limit 1)")).isZero();
        // El técnico actual es el de la última asignación registrada; toda reasignación tiene justificación.
        assertThat(count("select count(*) " + COHORT + " and t.technician_username is distinct from (select l.new_technician_username "
                + "from ticket_assignment_logs l where l.ticket_id = t.id order by l.assigned_at desc, l.id desc limit 1)")).isZero();
        assertThat(count("select count(*) from ticket_assignment_logs where assignment_type = 'REASSIGNMENT'")).isPositive();
        assertThat(count("select count(*) from ticket_assignment_logs where assignment_type = 'REASSIGNMENT' "
                + "and (justification is null or previous_technician_username is null)")).isZero();
        // Todas las FK apuntan a usuarios con el rol que corresponde.
        assertThat(count("select count(*) " + COHORT + " and t.technician_username is not null and not exists (select 1 from user_role r "
                + "where r.username = t.technician_username and r.role = 'TECHNICIAN')")).isZero();
    }

    @Test
    void loadingTwiceIsRejectedWithoutDuplicatingData() {
        assertThatThrownBy(this::runScript).hasMessageContaining("ya están cargados");
        assertThat(count("select count(*) " + COHORT)).isEqualTo(180);
        assertThat(count("select count(*) from knowledge_articles")).isEqualTo(31);
    }

    @Test
    void demoUsersCanLogInAndSeeOnlyTheirScope() throws Exception {
        String user = token("laura.gomez", DEMO_PASSWORD);
        String technician = token("tec.redes", DEMO_PASSWORD);
        String admin = token("admin.demo", DEMO_PASSWORD);

        assertThat(json(request("GET", "/api/tickets?size=1", null, user)).get("totalElements").asLong())
                .isEqualTo(count("select count(*) from tickets where applicant_username = 'laura.gomez'")).isPositive();
        assertThat(json(request("GET", "/api/tickets?size=1", null, technician)).get("totalElements").asLong())
                .isEqualTo(count("select count(*) from tickets where technician_username = 'tec.redes'")).isPositive();
        assertThat(json(request("GET", "/api/tickets?size=1", null, admin)).get("totalElements").asLong())
                .isEqualTo(count("select count(*) from tickets"));
        // Prueba obligatoria 4: los incidentes de una categoría coinciden con la base relacional.
        long connectivity = jdbc.queryForObject("select id from categories where name = 'Conectividad'", Long.class);
        assertThat(json(request("GET", "/api/tickets?size=1&categoryId=" + connectivity, null, admin)).get("totalElements").asLong())
                .isEqualTo(count("select count(*) from tickets where category_id = " + connectivity)).isPositive();
        assertThat(json(request("GET", "/api/knowledge/articles?size=100", null, user)).get("totalElements").asLong()).isEqualTo(28);
    }

    @Test
    void articleHashesMatchTheApplicationAndPublishedArticlesGetIndexed() throws Exception {
        String admin = token("admin.demo", DEMO_PASSWORD);
        JsonNode article = json(request("GET", "/api/knowledge/articles?size=1", null, admin)).get("content").get(0);
        long id = article.get("id").asLong();

        // Guardar el mismo contenido no crea una versión: el hash sembrado es el que calcula la aplicación.
        String same = mapper.createObjectNode().put("title", article.get("title").asText())
                .put("problem", article.get("problem").asText()).put("symptoms", article.get("symptoms").asText())
                .put("cause", article.get("cause").asText()).put("solution", article.get("solution").asText()).toString();
        JsonNode saved = json(request("PUT", "/api/knowledge/articles/" + id, same, admin));
        assertThat(saved.get("contentVersion").asInt()).isEqualTo(1);
        assertThat(saved.get("eligible").asBoolean()).isTrue();

        // Los publicados nacen PENDING y la reconciliación los envía a indexar; el retirado y los borradores no.
        HttpResponse<String> reconcile = request("POST", "/api/knowledge/articles/index/reconcile", null, admin);
        assertThat(reconcile.statusCode()).as(reconcile.body()).isEqualTo(200);
        assertThat(count("select count(*) from knowledge_articles where active and verified and index_status = 'INDEXED'")).isEqualTo(28);
        assertThat(count("select count(*) from knowledge_articles where not (active and verified) and index_status <> 'NOT_INDEXED'")).isZero();
        assertThat(n8n.indexCalls).hasSize(28);
    }

    /** Pruebas obligatorias 4 y 5 sobre el conjunto completo: la API coincide con SQL independiente. */
    @Test
    void analyticsOverTheFullDatasetMatchIndependentSql() throws Exception {
        String admin = token("admin.demo", DEMO_PASSWORD);
        String range = "from=2026-05-04&to=2026-11-08";
        String where = " from tickets t where t.created_at >= '2026-05-04' and t.created_at < '2026-11-09'";
        JsonNode summary = json(request("GET", "/api/analytics/summary?granularity=WEEK&" + range, null, admin));

        // 1. Total y 4. tasa de resolución.
        long total = count("select count(*)" + where);
        long resolved = count("select count(*)" + where + " and t.status in ('RESOLVED','CLOSED')");
        assertThat(summary.get("totalIncidents").asLong()).isEqualTo(total).isEqualTo(180);
        assertThat(summary.get("resolution").get("resolvedCount").asLong()).isEqualTo(resolved);
        assertThat(summary.get("resolution").get("ratePercentage").asDouble()).isCloseTo(100.0 * resolved / total, within(1e-9));

        // 2. Por categoría y 6. categoría más recurrente.
        List<Map<String, Object>> byCategory = jdbc.queryForList("select c.name, count(*) as n" + where.replace("from tickets t",
                "from tickets t join categories c on c.id = t.category_id") + " group by c.name order by n desc, c.name");
        assertThat(summary.get("byCategory")).hasSize(byCategory.size());
        for (JsonNode item : summary.get("byCategory")) {
            long expected = byCategory.stream().filter(row -> row.get("name").equals(item.get("category").asText()))
                    .mapToLong(row -> ((Number) row.get("n")).longValue()).findFirst().orElseThrow();
            assertThat(item.get("count").asLong()).as(item.get("category").asText()).isEqualTo(expected);
            assertThat(item.get("percentage").asDouble()).isCloseTo(100.0 * expected / total, within(1e-9));
        }
        assertThat(summary.get("mostRecurrentCategories").get(0).get("category").asText()).isEqualTo(byCategory.get(0).get("name"));

        // 3. Evolución semanal: cada semana (lunes) coincide con date_trunc('week') y la serie suma el total.
        long sum = 0;
        for (JsonNode point : summary.get("trend").get("points")) {
            long expected = count("select count(*)" + where + " and date_trunc('week', t.created_at)::date = '"
                    + point.get("periodStart").asText() + "'");
            assertThat(point.get("count").asLong()).as(point.get("periodStart").asText()).isEqualTo(expected);
            sum += point.get("count").asLong();
        }
        assertThat(sum).isEqualTo(total);
        assertThat(summary.get("trend").get("points")).hasSize(27);

        // 5. Tiempo promedio de resolución en horas, total y por categoría; ningún registro excluido.
        String resolvedWhere = where + " and t.status in ('RESOLVED','CLOSED') and t.resolved_at is not null and t.resolved_at >= t.created_at";
        Double hours = jdbc.queryForObject("select avg(extract(epoch from (t.resolved_at - t.created_at))) / 3600.0" + resolvedWhere, Double.class);
        JsonNode time = summary.get("resolutionTime");
        assertThat(time.get("averageHours").asDouble()).isCloseTo(hours, within(1e-6));
        assertThat(time.get("sampleSize").asLong()).isEqualTo(resolved);
        assertThat(time.get("excludedCount").asLong()).isZero();
        for (JsonNode item : time.get("byCategory")) {
            Double expected = jdbc.queryForObject("select avg(extract(epoch from (t.resolved_at - t.created_at))) / 3600.0"
                    + resolvedWhere + " and t.category_id = " + item.get("categoryId").asLong(), Double.class);
            assertThat(item.get("averageHours").asDouble()).as(item.get("category").asText()).isCloseTo(expected, within(1e-6));
        }

        // 7. Por prioridad.
        for (JsonNode item : summary.get("byPriority")) {
            assertThat(item.get("count").asLong()).as(item.get("priority").asText())
                    .isEqualTo(count("select count(*)" + where + " and t.priority = '" + item.get("priority").asText() + "'"));
        }

        // Filtros combinados de categoría, prioridad y fechas.
        long connectivity = jdbc.queryForObject("select id from categories where name = 'Conectividad'", Long.class);
        JsonNode filtered = json(request("GET", "/api/analytics/summary?from=2026-09-14&to=2026-09-20&priority=ALTA&categoryId="
                + connectivity, null, admin));
        assertThat(filtered.get("totalIncidents").asLong()).isEqualTo(count("select count(*) from tickets t where t.category_id = "
                + connectivity + " and t.priority = 'ALTA' and t.created_at >= '2026-09-14' and t.created_at < '2026-09-21'")).isPositive();
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}

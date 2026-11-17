package com.roles.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.JsonNode;
import com.roles.usermanagement.modules.dashboard.repository.AnalyticsQueryRepository;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Los indicadores de un mismo resumen salen de varias consultas: todas deben ver la misma
 * instantánea aunque otro usuario confirme un incidente entre una y otra.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AnalyticsSnapshotTests extends ApiTestSupport {

    @MockitoSpyBean
    private AnalyticsQueryRepository queries;

    @Test
    void ticketCommittedBetweenAggregationsDoesNotSplitTheSummary() throws Exception {
        String userToken = token(registerUser(), PASSWORD);
        createTicket(userToken);
        createTicket(userToken);
        AtomicBoolean inserted = new AtomicBoolean();
        // Tras calcular el total, otra petición registra y confirma un incidente de la misma cohorte.
        doAnswer(invocation -> {
            Object totals = invocation.callRealMethod();
            if (inserted.compareAndSet(false, true)) {
                createTicket(userToken);
            }
            return totals;
        }).when(queries).totalAndResolved(any());

        HttpResponse<String> response = request("GET", "/api/analytics/summary", null, userToken);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode summary = json(response);

        assertThat(inserted).isTrue();
        long total = summary.get("totalIncidents").asLong();
        assertThat(total).isEqualTo(2);
        assertThat(sum(summary.get("byCategory"))).isEqualTo(total);
        assertThat(sum(summary.get("byPriority"))).isEqualTo(total);
        assertThat(sum(summary.get("trend").get("points"))).isEqualTo(total);
        // La consulta siguiente sí ve el incidente nuevo: no hay caché.
        assertThat(json(request("GET", "/api/analytics/summary", null, userToken)).get("totalIncidents").asLong())
                .isEqualTo(3);
    }

    private static long sum(JsonNode items) {
        long sum = 0;
        for (JsonNode item : items) {
            sum += item.get("count").asLong();
        }
        return sum;
    }
}

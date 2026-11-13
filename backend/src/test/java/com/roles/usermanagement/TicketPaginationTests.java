package com.roles.usermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** B11: listados paginados, con límite máximo y orden estable. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TicketPaginationTests extends ApiTestSupport {

    @Test
    void pagesAreBoundedStableAndScopedToTheUser() throws Exception {
        String userToken = token(registerUser(), PASSWORD);
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(createTicket(userToken));
        }
        createTicket(token(registerUser(), PASSWORD));

        JsonNode first = json(request("GET", "/api/tickets?size=2", null, userToken));
        JsonNode second = json(request("GET", "/api/tickets?size=2&page=1", null, userToken));
        JsonNode third = json(request("GET", "/api/tickets?size=2&page=2", null, userToken));

        // Solo sus 5 tickets: el alcance se aplica antes de paginar.
        assertThat(first.get("totalElements").asLong()).isEqualTo(5);
        assertThat(first.get("totalPages").asInt()).isEqualTo(3);
        assertThat(first.get("size").asInt()).isEqualTo(2);
        List<Long> seen = new ArrayList<>();
        for (JsonNode page : List.of(first, second, third)) {
            page.get("content").forEach(item -> seen.add(item.get("id").asLong()));
        }
        // Más recientes primero, sin repetidos ni omitidos entre páginas.
        List<Long> expected = new ArrayList<>(created);
        java.util.Collections.reverse(expected);
        assertThat(seen).containsExactlyElementsOf(expected);

        assertThat(json(request("GET", "/api/tickets", null, userToken)).get("size").asInt()).isEqualTo(20);
        assertThat(request("GET", "/api/tickets?size=101", null, userToken).statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/tickets?size=0", null, userToken).statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/tickets?page=-1", null, userToken).statusCode()).isEqualTo(400);
    }
}

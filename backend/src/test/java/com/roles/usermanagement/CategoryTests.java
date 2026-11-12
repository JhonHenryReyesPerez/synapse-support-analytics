package com.roles.usermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** B06: categorías relacionales, sin duplicados por idioma, y prioridad canónica. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CategoryTests extends ApiTestSupport {

    @Test
    void catalogHasAtLeastFiveActiveCategories() throws Exception {
        String userToken = token(registerUser(), PASSWORD);

        List<String> names = categoryNames(userToken);

        assertThat(names).hasSizeGreaterThanOrEqualTo(5)
                .contains("Hardware", "Software", "Conectividad", "Accesos", "Otros");
    }

    @Test
    void legacyAliasesResolveToOneCategory() throws Exception {
        String userToken = token(registerUser(), PASSWORD);

        JsonNode english = json(createTicket(userToken, "\"category\":\"NETWORK\""));
        JsonNode spanish = json(createTicket(userToken, "\"category\":\"REDES\""));
        JsonNode byName = json(createTicket(userToken, "\"category\":\"conectividad\""));
        JsonNode byId = json(createTicket(userToken, "\"categoryId\":" + english.get("categoryId").asLong()));

        assertThat(english.get("category").asText()).isEqualTo("Conectividad");
        assertThat(List.of(spanish.get("categoryId").asLong(), byName.get("categoryId").asLong(), byId.get("categoryId").asLong()))
                .containsOnly(english.get("categoryId").asLong());
    }

    @Test
    void rejectsUnknownMissingAndInactiveCategories() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        String name = "Temporal " + UUID.randomUUID().toString().substring(0, 8);
        HttpResponse<String> created = request("POST", "/api/categories", "{\"name\":\"" + name + "\"}", admin);
        assertThat(created.statusCode()).isEqualTo(201);
        long id = json(created).get("id").asLong();

        assertThat(createTicket(userToken, "\"category\":\"NO_EXISTE\"").statusCode()).isEqualTo(400);
        assertThat(createTicket(userToken, "\"categoryId\":999999").statusCode()).isEqualTo(400);
        assertThat(createTicket(userToken, "\"title\":\"Sin categoría alguna\"").statusCode()).isEqualTo(400);
        assertThat(createTicket(userToken, "\"categoryId\":" + id).statusCode()).isEqualTo(201);

        assertThat(request("PATCH", "/api/categories/" + id + "/deactivate", null, admin).statusCode()).isEqualTo(200);

        assertThat(createTicket(userToken, "\"categoryId\":" + id).statusCode()).isEqualTo(400);
        assertThat(categoryNames(userToken)).doesNotContain(name);
        // El ticket anterior conserva su categoría aunque ya no esté activa.
        assertThat(json(request("GET", "/api/tickets?categoryId=" + id, null, userToken))).hasSize(1);
    }

    @Test
    void onlyAdminManagesCatalogAndNamesAreUnique() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);

        assertThat(request("POST", "/api/categories", "{\"name\":\"Intento\"}", userToken).statusCode()).isEqualTo(403);
        assertThat(request("POST", "/api/categories", "{\"name\":\"hardware\"}", admin).statusCode()).isEqualTo(409);
        assertThat(request("POST", "/api/categories", "{\"name\":\"   \"}", admin).statusCode()).isEqualTo(400);
    }

    @Test
    void filtersByCategoryAndCanonicalPriority() throws Exception {
        String userToken = token(registerUser(), PASSWORD);
        createTicket(userToken, "\"category\":\"HARDWARE\",\"priority\":\"HIGH\"");
        createTicket(userToken, "\"category\":\"SOFTWARE\",\"priority\":\"BAJA\"");

        JsonNode hardware = json(request("GET", "/api/tickets?category=HARDWARE", null, userToken));
        JsonNode high = json(request("GET", "/api/tickets?priority=HIGH", null, userToken));

        assertThat(hardware).hasSize(1);
        assertThat(hardware.get(0).get("category").asText()).isEqualTo("Hardware");
        // HIGH es solo un alias de entrada: se almacena y devuelve ALTA.
        assertThat(high).hasSize(1);
        assertThat(high.get(0).get("priority").asText()).isEqualTo("ALTA");
        assertThat(request("GET", "/api/tickets?priority=URGENTE", null, userToken).statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/tickets?category=NO_EXISTE", null, userToken).statusCode()).isEqualTo(400);
    }

    /** Crea un ticket sobrescribiendo los campos indicados (fragmento JSON sin llaves). */
    private HttpResponse<String> createTicket(String userToken, String overrides) throws Exception {
        var node = mapper.createObjectNode();
        node.put("title", "Equipo sin acceso a la red");
        node.put("description", "No obtiene dirección IP desde esta mañana");
        node.put("priority", "MEDIA");
        node.setAll((com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree("{" + overrides + "}"));
        return request("POST", "/api/tickets", node.toString(), userToken);
    }

    private List<String> categoryNames(String token) throws Exception {
        HttpResponse<String> response = request("GET", "/api/categories", null, token);
        assertThat(response.statusCode()).isEqualTo(200);
        List<String> names = new ArrayList<>();
        json(response).forEach(item -> names.add(item.get("name").asText()));
        return names;
    }
}

package com.roles.usermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** RF02: ciclo editorial de los artículos y quién puede verlos o modificarlos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KnowledgeArticleTests extends ApiTestSupport {

    @Test
    void articleIsBornAsDraftAndOnlyVerifiedOnesCanBePublished() throws Exception {
        String admin = adminToken();
        String techToken = token(createTechnician(), PASSWORD);
        String userToken = token(registerUser(), PASSWORD);

        // USER no redacta; los campos privilegiados del cuerpo se ignoran.
        assertThat(request("POST", "/api/knowledge/articles", body("Sin red", "v1"), userToken).statusCode()).isEqualTo(403);
        HttpResponse<String> created = request("POST", "/api/knowledge/articles",
                body("Sin red", "v1").replace("}", ",\"verified\":true,\"active\":true,\"contentVersion\":9,\"authorUsername\":\"" + ADMIN + "\"}"),
                techToken);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        JsonNode draft = json(created);
        long id = draft.get("id").asLong();
        assertThat(draft.get("verified").asBoolean()).isFalse();
        assertThat(draft.get("active").asBoolean()).isFalse();
        assertThat(draft.get("eligible").asBoolean()).isFalse();
        assertThat(draft.get("contentVersion").asInt()).isEqualTo(1);
        assertThat(draft.get("authorUsername").asText()).isNotEqualTo(ADMIN);
        assertThat(draft.get("indexStatus").asText()).isEqualTo("NOT_INDEXED");

        // Un borrador no es público ni activable; verificar y activar es exclusivo de ADMIN.
        assertThat(request("GET", "/api/knowledge/articles/" + id, null, userToken).statusCode()).isEqualTo(403);
        assertThat(request("POST", "/api/knowledge/articles/" + id + "/activate", null, admin).statusCode()).isEqualTo(409);
        assertThat(request("POST", "/api/knowledge/articles/" + id + "/verify", null, techToken).statusCode()).isEqualTo(403);
        assertThat(request("POST", "/api/knowledge/articles/" + id + "/activate", null, techToken).statusCode()).isEqualTo(403);

        JsonNode verified = json(request("POST", "/api/knowledge/articles/" + id + "/verify", null, admin));
        assertThat(verified.get("verified").asBoolean()).isTrue();
        assertThat(verified.get("verifiedByUsername").asText()).isEqualTo(ADMIN);
        assertThat(verified.get("verifiedAt").isNull()).isFalse();
        assertThat(verified.get("eligible").asBoolean()).isFalse();

        JsonNode active = json(request("POST", "/api/knowledge/articles/" + id + "/activate", null, admin));
        assertThat(active.get("eligible").asBoolean()).isTrue();
        assertThat(active.get("indexStatus").asText()).isEqualTo("INDEXED");

        // Publicado: cualquier rol lo consulta y aparece en el listado por defecto.
        assertThat(request("GET", "/api/knowledge/articles/" + id, null, userToken).statusCode()).isEqualTo(200);
        assertThat(publishedIds(userToken)).contains(id);
    }

    @Test
    void contentChangeCreatesNewVersionAndRevokesEligibility() throws Exception {
        String admin = adminToken();
        String techToken = token(createTechnician(), PASSWORD);
        String otherTechToken = token(createTechnician(), PASSWORD);
        long id = json(request("POST", "/api/knowledge/articles", body("VPN no conecta", "v1"), techToken)).get("id").asLong();

        // Otro técnico no ve ni edita el borrador ajeno; el autor sí lo edita.
        assertThat(request("GET", "/api/knowledge/articles/" + id, null, otherTechToken).statusCode()).isEqualTo(403);
        assertThat(request("PUT", "/api/knowledge/articles/" + id, body("VPN no conecta", "v2"), otherTechToken).statusCode()).isEqualTo(403);
        assertThat(json(request("PUT", "/api/knowledge/articles/" + id, body("VPN no conecta", "v2"), techToken))
                .get("contentVersion").asInt()).isEqualTo(2);

        request("POST", "/api/knowledge/articles/" + id + "/verify", null, admin);
        request("POST", "/api/knowledge/articles/" + id + "/activate", null, admin);

        // Verificado: el técnico ya no lo modifica.
        assertThat(request("PUT", "/api/knowledge/articles/" + id, body("VPN no conecta", "v3"), techToken).statusCode()).isEqualTo(409);

        // Guardar sin cambios no altera versión ni elegibilidad.
        JsonNode unchanged = json(request("PUT", "/api/knowledge/articles/" + id, body("VPN no conecta", "v2"), admin));
        assertThat(unchanged.get("contentVersion").asInt()).isEqualTo(2);
        assertThat(unchanged.get("eligible").asBoolean()).isTrue();

        // Cambio técnico: versión nueva, verificación revocada, inactivo y fuera del listado público.
        JsonNode changed = json(request("PUT", "/api/knowledge/articles/" + id, body("VPN no conecta", "v3"), admin));
        assertThat(changed.get("contentVersion").asInt()).isEqualTo(3);
        assertThat(changed.get("verified").asBoolean()).isFalse();
        assertThat(changed.get("verifiedByUsername").isNull()).isTrue();
        assertThat(changed.get("active").asBoolean()).isFalse();
        assertThat(changed.get("eligible").asBoolean()).isFalse();
        assertThat(publishedIds(techToken)).doesNotContain(id);
        // La reactivación exige una nueva revisión.
        assertThat(request("POST", "/api/knowledge/articles/" + id + "/activate", null, admin).statusCode()).isEqualTo(409);
    }

    @Test
    void deactivationHidesArticleWithoutDeletingIt() throws Exception {
        String admin = adminToken();
        String userToken = token(registerUser(), PASSWORD);
        long id = json(request("POST", "/api/knowledge/articles", body("Impresora atascada", "v1"), admin)).get("id").asLong();
        request("POST", "/api/knowledge/articles/" + id + "/verify", null, admin);
        request("POST", "/api/knowledge/articles/" + id + "/activate", null, admin);

        JsonNode inactive = json(request("POST", "/api/knowledge/articles/" + id + "/deactivate", null, admin));

        assertThat(inactive.get("active").asBoolean()).isFalse();
        assertThat(inactive.get("verified").asBoolean()).isTrue();
        assertThat(publishedIds(userToken)).doesNotContain(id);
        assertThat(request("GET", "/api/knowledge/articles/" + id, null, userToken).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/knowledge/articles/" + id, null, admin).statusCode()).isEqualTo(200);
        // No existe borrado; un id inexistente responde 404 y el listado completo es solo para ADMIN.
        assertThat(request("DELETE", "/api/knowledge/articles/" + id, null, admin).statusCode()).isIn(404, 405);
        assertThat(request("GET", "/api/knowledge/articles/999999999", null, admin).statusCode()).isEqualTo(404);
        assertThat(request("GET", "/api/knowledge/articles?scope=ALL", null, userToken).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/knowledge/articles?scope=MINE", null, userToken).statusCode()).isEqualTo(403);
    }

    @Test
    void requiresEveryTechnicalField() throws Exception {
        String admin = adminToken();
        for (String field : new String[]{"title", "problem", "symptoms", "cause", "solution"}) {
            var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(body("Completo", "v1"));
            node.put(field, "   ");
            assertThat(request("POST", "/api/knowledge/articles", node.toString(), admin).statusCode()).as(field).isEqualTo(400);
        }
    }

    static String body(String title, String variant) {
        return "{\"title\":\"" + title + "\","
                + "\"problem\":\"Problema " + variant + "\",\"symptoms\":\"Síntomas " + variant + "\","
                + "\"cause\":\"Causa " + variant + "\",\"solution\":\"Solución " + variant + "\"}";
    }

    private java.util.List<Long> publishedIds(String token) throws Exception {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        json(request("GET", "/api/knowledge/articles?size=100", null, token)).get("content")
                .forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }
}

package com.roles.usermanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Utilidades compartidas por las pruebas de integración HTTP: cada prueba crea sus propios
 * usuarios y tickets y recupera sus IDs reales, sin depender del orden ni de ticketId=1.
 */
abstract class ApiTestSupport {

    protected static final String PASSWORD = "Password123!";
    protected static final String ADMIN = "superadmin";
    protected static final String ADMIN_PASSWORD = "SuperAdmin123!";

    @Autowired
    protected Environment environment;

    protected final HttpClient client = HttpClient.newHttpClient();
    protected final ObjectMapper mapper = new ObjectMapper();

    protected String adminToken() throws Exception {
        return token(ADMIN, ADMIN_PASSWORD);
    }

    /** Registra un USER nuevo y devuelve su username. */
    protected String registerUser() throws Exception {
        String user = "u_" + UUID.randomUUID().toString().substring(0, 10);
        String body = String.format("{\"username\":\"%s\",\"email\":\"%s@test.local\",\"password\":\"%s\"}", user, user, PASSWORD);
        assertThat(request("POST", "/api/auth/register", body, null).statusCode()).isEqualTo(201);
        return user;
    }

    /** Crea un TECHNICIAN activo sin cambio de contraseña pendiente y devuelve su username. */
    protected String createTechnician() throws Exception {
        String user = "t_" + UUID.randomUUID().toString().substring(0, 10);
        String body = String.format(
                "{\"username\":\"%s\",\"email\":\"%s@test.local\",\"password\":\"%s\",\"role\":\"TECHNICIAN\",\"mustChangePassword\":false}",
                user, user, PASSWORD);
        assertThat(request("POST", "/api/user/add", body, adminToken()).statusCode()).isEqualTo(200);
        return user;
    }

    /** Crea un ticket OPEN con el token indicado y devuelve su ID real. */
    protected long createTicket(String userToken) throws Exception {
        String body = "{\"title\":\"Sin conexión a la red\",\"description\":\"El equipo no obtiene dirección IP\","
                + "\"category\":\"NETWORK\",\"priority\":\"HIGH\"}";
        HttpResponse<String> response = request("POST", "/api/tickets", body, userToken);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json(response).get("id").asLong();
    }

    protected String token(String user, String password) throws Exception {
        HttpResponse<String> response = request("POST", "/api/auth/login",
                String.format("{\"username\":\"%s\",\"password\":\"%s\"}", user, password), null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json(response).get("token").asText();
    }

    protected JsonNode json(HttpResponse<String> response) throws Exception {
        return mapper.readTree(response.body());
    }

    protected HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        int port = Integer.parseInt(environment.getProperty("local.server.port", "8080"));
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}

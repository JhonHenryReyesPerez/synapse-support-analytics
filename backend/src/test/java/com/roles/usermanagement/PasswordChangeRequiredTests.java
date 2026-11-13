package com.roles.usermanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B03: una cuenta con cambio de contraseña pendiente solo puede usar las operaciones
 * mínimas de sesión hasta cambiar la clave. Cada prueba crea su propio usuario.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PasswordChangeRequiredTests {

    private static final String INITIAL = "Password123!";
    private static final String TEMPORARY = "Temporal#2026";

    @Autowired
    private Environment environment;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void pendingAccountIsRejectedOnDirectApiCalls() throws Exception {
        String token = tokenOfUserWithPendingChange();

        HttpResponse<String> tickets = request("GET", "/api/tickets", null, token);

        assertThat(tickets.statusCode()).isEqualTo(403);
        assertThat(mapper.readTree(tickets.body()).get("code").asText()).isEqualTo("PASSWORD_CHANGE_REQUIRED");
        assertThat(request("POST", "/api/tickets",
                "{\"title\":\"x\",\"description\":\"y\",\"category\":\"SOFTWARE\",\"priority\":\"LOW\"}", token)
                .statusCode()).isEqualTo(403);
    }

    @Test
    void pendingAccountKeepsSessionEndpoints() throws Exception {
        String token = tokenOfUserWithPendingChange();

        assertThat(request("GET", "/api/auth/me", null, token).statusCode()).isEqualTo(200);
    }

    @Test
    void changingPasswordLiftsTheRestriction() throws Exception {
        String token = tokenOfUserWithPendingChange();
        assertThat(request("GET", "/api/tickets", null, token).statusCode()).isEqualTo(403);

        HttpResponse<String> change = request("POST", "/api/auth/change-password",
                String.format("{\"currentPassword\":\"%s\",\"newPassword\":\"Definitiva#1\"}", TEMPORARY), token);
        assertThat(change.statusCode()).isEqualTo(200);

        // B04: el cambio invalida el token anterior y devuelve uno nuevo.
        String renewed = mapper.readTree(change.body()).get("token").asText();
        assertThat(request("GET", "/api/tickets", null, renewed).statusCode()).isEqualTo(200);
    }

    @Test
    void accountWithoutPendingChangeIsNotRestricted() throws Exception {
        String user = registerUser();
        HttpResponse<String> login = login(user, INITIAL);

        assertThat(mapper.readTree(login.body()).get("mustChangePassword").asBoolean()).isFalse();
        assertThat(request("GET", "/api/tickets", null, token(login)).statusCode()).isEqualTo(200);
    }

    private String tokenOfUserWithPendingChange() throws Exception {
        String user = registerUser();
        String adminToken = token(login("superadmin", "SuperAdmin123!"));
        assertThat(request("POST", "/api/user/" + user + "/reset-password",
                String.format("{\"temporaryPassword\":\"%s\"}", TEMPORARY), adminToken).statusCode()).isEqualTo(200);

        HttpResponse<String> login = login(user, TEMPORARY);
        assertThat(mapper.readTree(login.body()).get("mustChangePassword").asBoolean()).isTrue();
        return token(login);
    }

    private String registerUser() throws Exception {
        String user = "b03_" + UUID.randomUUID().toString().substring(0, 8);
        String body = String.format("{\"username\":\"%s\",\"email\":\"%s@test.local\",\"password\":\"%s\"}", user, user, INITIAL);
        assertThat(request("POST", "/api/auth/register", body, null).statusCode()).isEqualTo(201);
        return user;
    }

    private HttpResponse<String> login(String user, String password) throws Exception {
        return request("POST", "/api/auth/login",
                String.format("{\"username\":\"%s\",\"password\":\"%s\"}", user, password), null);
    }

    private String token(HttpResponse<String> loginResponse) throws Exception {
        assertThat(loginResponse.statusCode()).isEqualTo(200);
        return mapper.readTree(loginResponse.body()).get("token").asText();
    }

    private HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
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

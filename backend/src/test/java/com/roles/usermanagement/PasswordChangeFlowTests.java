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
 * B02: la política se aplica al establecer contraseñas y el login solo autentica.
 * Cada prueba usa su propio usuario para no depender del orden de ejecución.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PasswordChangeFlowTests {

    private static final String INITIAL = "Password123!";

    @Autowired
    private Environment environment;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptedNewPasswordAllowsLoginAndOldOneStopsWorking() throws Exception {
        String user = registerUser();
        String token = token(login(user, INITIAL));

        assertThat(changePassword(token, INITIAL, "NuevaClave#1").statusCode()).isEqualTo(200);

        assertThat(login(user, "NuevaClave#1").statusCode()).isEqualTo(200);
        assertThat(login(user, INITIAL).statusCode()).isEqualTo(401);
    }

    @Test
    void changeRejectsPasswordThatBreaksPolicyAndKeepsCurrentOne() throws Exception {
        String user = registerUser();
        String token = token(login(user, INITIAL));

        assertThat(changePassword(token, INITIAL, "abcdef").statusCode()).isEqualTo(400);

        assertThat(login(user, INITIAL).statusCode()).isEqualTo(200);
    }

    @Test
    void loginWithWrongPasswordIsUnauthorizedEvenIfItBreaksPolicy() throws Exception {
        String user = registerUser();

        HttpResponse<String> response = login(user, "abc");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void adminResetRejectsPasswordThatBreaksPolicy() throws Exception {
        String user = registerUser();
        String adminToken = token(login("superadmin", "SuperAdmin123!"));

        HttpResponse<String> response = request("POST", "/api/user/" + user + "/reset-password",
                "{\"temporaryPassword\":\"abcdef\"}", adminToken);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(login(user, INITIAL).statusCode()).isEqualTo(200);
    }

    private String registerUser() throws Exception {
        String user = "b02_" + UUID.randomUUID().toString().substring(0, 8);
        String body = String.format("{\"username\":\"%s\",\"email\":\"%s@test.local\",\"password\":\"%s\"}", user, user, INITIAL);
        assertThat(request("POST", "/api/auth/register", body, null).statusCode()).isEqualTo(201);
        return user;
    }

    private HttpResponse<String> login(String user, String password) throws Exception {
        return request("POST", "/api/auth/login",
                String.format("{\"username\":\"%s\",\"password\":\"%s\"}", user, password), null);
    }

    private HttpResponse<String> changePassword(String token, String current, String next) throws Exception {
        return request("POST", "/api/auth/change-password",
                String.format("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}", current, next), token);
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

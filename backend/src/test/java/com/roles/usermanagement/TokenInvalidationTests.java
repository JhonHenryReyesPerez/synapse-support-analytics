package com.roles.usermanagement;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roles.usermanagement.domain.service.PasswordPolicy;
import com.roles.usermanagement.domain.service.TemporaryPasswordGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B04: los tokens emitidos antes de un cambio o restablecimiento de contraseña dejan de servir.
 * Cada prueba crea su propio usuario.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TokenInvalidationTests {

    private static final String INITIAL = "Password123!";

    @Autowired
    private Environment environment;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void previousTokenStopsWorkingAfterPasswordChange() throws Exception {
        String user = registerUser();
        String oldToken = token(login(user, INITIAL));
        assertThat(isAccepted(oldToken)).isTrue();

        HttpResponse<String> change = request("POST", "/api/auth/change-password",
                String.format("{\"currentPassword\":\"%s\",\"newPassword\":\"NuevaClave#1\"}", INITIAL), oldToken);
        assertThat(change.statusCode()).isEqualTo(200);

        assertThat(isAccepted(oldToken)).isFalse();
        assertThat(isAccepted(mapper.readTree(change.body()).get("token").asText())).isTrue();
        assertThat(isAccepted(token(login(user, "NuevaClave#1")))).isTrue();
    }

    @Test
    void previousTokenStopsWorkingAfterAdminReset() throws Exception {
        String user = registerUser();
        String oldToken = token(login(user, INITIAL));
        String adminToken = token(login("superadmin", "SuperAdmin123!"));

        HttpResponse<String> reset = request("POST", "/api/user/" + user + "/reset-password", null, adminToken);
        assertThat(reset.statusCode()).isEqualTo(200);

        assertThat(isAccepted(oldToken)).isFalse();
        // Sin clave indicada se genera una aleatoria válida, no una fija.
        String temporary = mapper.readTree(reset.body()).get("temporaryPassword").asText();
        assertThat(temporary).isNotEqualTo("Temporal123!");
        assertThat(PasswordPolicy.isValid(temporary)).isTrue();
        assertThat(login(user, temporary).statusCode()).isEqualTo(200);
    }

    @Test
    void tokenWithoutCredentialVersionIsRejected() throws Exception {
        String user = registerUser();
        String legacy = JWT.create().withSubject(user)
                .withIssuer(environment.getProperty("security.jwt.issuer"))
                .withExpiresAt(Instant.now().plusSeconds(300))
                .sign(Algorithm.HMAC256(environment.getProperty("security.jwt.secret")));

        assertThat(isAccepted(legacy)).isFalse();
    }

    @Test
    void generatedTemporaryPasswordsAreValidAndDifferent() {
        String first = TemporaryPasswordGenerator.generate();
        String second = TemporaryPasswordGenerator.generate();

        assertThat(PasswordPolicy.isValid(first)).isTrue();
        assertThat(PasswordPolicy.isValid(second)).isTrue();
        assertThat(first).isNotEqualTo(second);
    }

    /** /api/auth/me exige sesión válida y no está sujeto a la restricción de B03. */
    private boolean isAccepted(String token) throws Exception {
        return request("GET", "/api/auth/me", null, token).statusCode() == 200;
    }

    private String registerUser() throws Exception {
        String user = "b04_" + UUID.randomUUID().toString().substring(0, 8);
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

package com.roles.usermanagement;

import com.roles.usermanagement.web.config.LoginAttemptLimiter;
import com.roles.usermanagement.web.config.LoginAttemptProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** §14: control de intentos de login. El bloqueo es temporal, por identificador y no revela si la cuenta existe. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LoginAttemptLimitTests extends ApiTestSupport {

    @Test
    void repeatedFailuresBlockTheIdentifierEvenWithTheRightPassword() throws Exception {
        String user = registerUser();
        String other = registerUser();

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(login(user, "Incorrecta123!").statusCode()).as("intento " + attempt).isEqualTo(401);
        }

        HttpResponse<String> blocked = login(user, PASSWORD);
        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(json(blocked).get("code").asText()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
        assertThat(blocked.headers().firstValueAsLong("Retry-After").orElse(0)).isBetween(1L, 900L);
        // El identificador se normaliza: variar mayúsculas no evita el bloqueo.
        assertThat(login(user.toUpperCase(), PASSWORD).statusCode()).isEqualTo(429);
        // El bloqueo es por identificador: otra cuenta sigue entrando.
        assertThat(login(other, PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    void unknownIdentifierIsBlockedTheSameWay() throws Exception {
        String ghost = "ghost_" + UUID.randomUUID().toString().substring(0, 8);
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(login(ghost, "Incorrecta123!").statusCode()).isEqualTo(401);
        }
        assertThat(login(ghost, "Incorrecta123!").statusCode()).isEqualTo(429);
    }

    @Test
    void successfulLoginResetsTheCounter() throws Exception {
        String user = registerUser();
        for (int round = 0; round < 2; round++) {
            for (int attempt = 1; attempt <= 4; attempt++) {
                assertThat(login(user, "Incorrecta123!").statusCode()).isEqualTo(401);
            }
            assertThat(login(user, PASSWORD).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void lockAndWindowExpireWithTime() {
        MutableClock clock = new MutableClock();
        var limiter = new LoginAttemptLimiter(
                new LoginAttemptProperties(3, Duration.ofMinutes(10), Duration.ofMinutes(5)), clock);

        limiter.recordFailure("ana");
        limiter.recordFailure("ana");
        clock.advance(Duration.ofMinutes(11));
        // La ventana venció: los dos fallos anteriores ya no cuentan.
        limiter.recordFailure("ana");
        limiter.recordFailure(" ANA ");
        assertThat(limiter.retryAfter("ana")).isEmpty();
        limiter.recordFailure("ana");
        assertThat(limiter.retryAfter("ana")).contains(Duration.ofMinutes(5));

        clock.advance(Duration.ofMinutes(5));
        assertThat(limiter.retryAfter("ana")).isEmpty();
        // Tras el bloqueo empieza una cuenta nueva.
        limiter.recordFailure("ana");
        assertThat(limiter.retryAfter("ana")).isEmpty();
    }

    private HttpResponse<String> login(String identifier, String password) throws Exception {
        return request("POST", "/api/auth/login",
                String.format("{\"username\":\"%s\",\"password\":\"%s\"}", identifier, password), null);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-13T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

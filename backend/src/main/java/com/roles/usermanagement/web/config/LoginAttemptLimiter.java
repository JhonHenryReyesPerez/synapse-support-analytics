package com.roles.usermanagement.web.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Control de intentos de login por identificador (usuario o correo tal como se envía).
 *
 * <p>Cuenta fallos sin comprobar si la cuenta existe, de modo que la respuesta no revela qué
 * identificadores son válidos. El estado vive en memoria: se pierde al reiniciar y no se comparte
 * entre instancias. Es un endurecimiento contra fuerza bruta, no un bloqueo permanente de cuentas.
 */
@Component
@EnableConfigurationProperties(LoginAttemptProperties.class)
public class LoginAttemptLimiter {

    /** Tope de identificadores seguidos a la vez, para que el mapa no crezca sin límite. */
    static final int MAX_TRACKED = 10_000;
    private static final int MAX_KEY_LENGTH = 320;

    private record Attempts(int failures, Instant windowStart, Instant lockedUntil) {
        boolean expired(Instant now, Duration window) {
            return lockedUntil != null ? !now.isBefore(lockedUntil) : !now.isBefore(windowStart.plus(window));
        }
    }

    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final LoginAttemptProperties properties;
    private final Clock clock;

    @Autowired
    public LoginAttemptLimiter(LoginAttemptProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public LoginAttemptLimiter(LoginAttemptProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Tiempo restante de bloqueo del identificador; vacío si puede intentar autenticarse. */
    public Optional<Duration> retryAfter(String identifier) {
        Instant now = clock.instant();
        Attempts current = attempts.get(key(identifier));
        if (current == null || current.lockedUntil() == null || !now.isBefore(current.lockedUntil())) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, current.lockedUntil()));
    }

    public void recordFailure(String identifier) {
        Instant now = clock.instant();
        String key = key(identifier);
        if (!attempts.containsKey(key) && attempts.size() >= MAX_TRACKED) {
            attempts.values().removeIf(entry -> entry.expired(now, properties.window()));
            if (attempts.size() >= MAX_TRACKED) {
                return;
            }
        }
        attempts.compute(key, (ignored, current) -> {
            if (current == null || current.expired(now, properties.window())) {
                current = new Attempts(0, now, null);
            }
            int failures = current.failures() + 1;
            Instant lockedUntil = failures >= properties.maxAttempts() ? now.plus(properties.lockDuration()) : null;
            return new Attempts(failures, current.windowStart(), lockedUntil);
        });
    }

    public void recordSuccess(String identifier) {
        attempts.remove(key(identifier));
    }

    private static String key(String identifier) {
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        return normalized.length() > MAX_KEY_LENGTH ? normalized.substring(0, MAX_KEY_LENGTH) : normalized;
    }
}

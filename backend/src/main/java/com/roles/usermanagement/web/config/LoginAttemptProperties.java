package com.roles.usermanagement.web.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * §14: control de intentos de login. Tras {@code maxAttempts} fallos dentro de {@code window},
 * el identificador queda bloqueado durante {@code lockDuration}.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.login")
public record LoginAttemptProperties(
        @Min(1) int maxAttempts,
        @NotNull Duration window,
        @NotNull Duration lockDuration) {

    @AssertTrue(message = "app.security.login.window y lock-duration deben ser de al menos un segundo")
    public boolean isDurationsValid() {
        return window != null && lockDuration != null
                && window.compareTo(Duration.ofSeconds(1)) >= 0
                && lockDuration.compareTo(Duration.ofSeconds(1)) >= 0;
    }
}

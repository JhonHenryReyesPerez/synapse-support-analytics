package com.roles.usermanagement.modules.assistance.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Parámetros del asistente RAG (RF04, RF05). El umbral es configurable y debe justificarse con casos de prueba. */
@Validated
@ConfigurationProperties(prefix = "app.assistance")
public record AssistanceProperties(
        /** Máximo de artículos a recuperar: el enunciado pide entre tres y cinco soluciones. */
        @Min(3) @Max(5) int maxResults,
        /** Umbral mínimo de similitud coseno; ningún artículo por debajo se acepta como fuente. */
        @DecimalMin("0.0") @DecimalMax("1.0") double minScore,
        /** Edad a partir de la cual una consulta PENDING se considera abandonada y se cierra como ERROR. */
        @NotNull Duration pendingTimeout
) {
    @AssertTrue(message = "ASSIST_PENDING_TIMEOUT debe ser una duración positiva")
    public boolean isPendingTimeoutPositive() {
        return pendingTimeout != null && !pendingTimeout.isNegative() && !pendingTimeout.isZero();
    }
}

package com.roles.usermanagement.integration.n8n;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Integración con n8n. La URL es fija por configuración: ningún cliente elige el destino HTTP.
 * Sin baseUrl la integración queda deshabilitada y las operaciones terminan en error controlado.
 */
@Validated
@ConfigurationProperties(prefix = "app.n8n")
public record N8nProperties(
        /** URL base de n8n, por ejemplo http://n8n:5678. Vacía = integración no configurada. */
        String baseUrl,
        /** Secreto que Spring Boot envía a los webhooks de n8n (cabecera X-Service-Token). */
        String serviceToken,
        /** Secreto que n8n envía a /api/integration/** (cabecera X-Integration-Token). Vacío = endpoints cerrados. */
        String inboundToken,
        @NotBlank String indexPath,
        @NotBlank String assistPath,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        /** Tamaño máximo aceptado de una respuesta de n8n, en bytes. */
        int maxResponseBytes
) {
    public static final int MIN_TOKEN_LENGTH = 32;

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    @AssertTrue(message = "app.n8n.base-url debe comenzar por http:// o https://")
    public boolean isBaseUrlValid() {
        return !isConfigured() || baseUrl.startsWith("http://") || baseUrl.startsWith("https://");
    }

    @AssertTrue(message = "N8N_SERVICE_TOKEN es obligatorio (mínimo 32 caracteres) cuando se configura N8N_BASE_URL")
    public boolean isServiceTokenValid() {
        return !isConfigured() || (serviceToken != null && serviceToken.length() >= MIN_TOKEN_LENGTH);
    }

    @AssertTrue(message = "INTEGRATION_API_TOKEN debe tener al menos 32 caracteres")
    public boolean isInboundTokenValid() {
        return inboundToken == null || inboundToken.isBlank() || inboundToken.length() >= MIN_TOKEN_LENGTH;
    }

    @AssertTrue(message = "Los tiempos límite de n8n deben ser positivos y maxResponseBytes mayor que cero")
    public boolean isLimitsValid() {
        return connectTimeout != null && !connectTimeout.isNegative() && !connectTimeout.isZero()
                && readTimeout != null && !readTimeout.isNegative() && !readTimeout.isZero()
                && maxResponseBytes > 0;
    }
}

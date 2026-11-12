package com.roles.usermanagement.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Configuration to allow CORS (Cross-Origin Resource Sharing) requests.
 */
@Configuration
public class CorsConfig {

    /**
     * Defines the permitted CORS configuration for the application.
     *
     * @return CORS configuration source.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:}") java.util.List<String> allowedOrigins) {
        // CORS configuration
        CorsConfiguration corsConfiguration = new CorsConfiguration();

        // Orígenes concretos configurados por entorno (CORS_ALLOWED_ORIGINS); sin configuración no se admite ninguno.
        corsConfiguration.setAllowedOrigins(allowedOrigins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList());
        // Allow specific HTTP methods (GET, POST, PUT, PATCH, DELETE, OPTIONS)
        corsConfiguration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Allow all headers in requests
        corsConfiguration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));

        // URL-based CORS configuration
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);

        return source;
    }
}

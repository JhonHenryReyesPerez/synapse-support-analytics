package com.roles.usermanagement.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI userManagementOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("CampusDesk API")
                        .version("1.0")
                        .description("Incidentes tecnológicos, base de conocimiento, asistente RAG, calificaciones y analítica. "
                                + "Cada operación requiere un JWT (Authorize) salvo el login y el registro. El asistente se consulta "
                                + "siempre por esta API: el frontend no llama a n8n, Qdrant ni al proveedor de modelos. "
                                + "Los endpoints /api/integration/** son un contrato de servicio para n8n y usan X-Integration-Token."))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")
                                .description("Introduce el token JWT sin el prefijo Bearer.")));
    }
}

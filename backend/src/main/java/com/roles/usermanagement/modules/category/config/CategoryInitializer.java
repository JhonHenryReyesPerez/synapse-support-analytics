package com.roles.usermanagement.modules.category.config;

import com.roles.usermanagement.modules.category.entity.Category;
import com.roles.usermanagement.modules.category.repository.CategoryRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Garantiza el catálogo base de categorías (idempotente por nombre). En PostgreSQL ya lo crea la
 * migración V3; este inicializador cubre los entornos sin Flyway (pruebas en H2).
 */
@Configuration
public class CategoryInitializer {

    public static final Map<String, String> BASE_CATEGORIES = baseCategories();

    @Bean
    public ApplicationRunner initializeCategories(CategoryRepository repository) {
        return args -> BASE_CATEGORIES.forEach((name, description) -> {
            if (!repository.existsByNameIgnoreCase(name)) {
                repository.save(Category.builder().name(name).description(description).active(true).build());
            }
        });
    }

    private static Map<String, String> baseCategories() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("Hardware", "Equipos de cómputo, periféricos e impresoras");
        result.put("Software", "Sistema operativo, instalación y licenciamiento de programas");
        result.put("Conectividad", "Red cableada, Wi-Fi, VPN e Internet");
        result.put("Accesos", "Cuentas, contraseñas y permisos");
        result.put("Aplicaciones", "Fallos de ejecución de aplicaciones institucionales");
        result.put("Servicios informáticos", "Correo, almacenamiento, respaldo y otros servicios");
        result.put("Otros", "Solicitudes que no encajan en las demás categorías");
        return java.util.Collections.unmodifiableMap(result);
    }
}

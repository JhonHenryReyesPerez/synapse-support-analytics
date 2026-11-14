package com.roles.usermanagement.web.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Respuestas JSON uniformes para los fallos de seguridad que ocurren en la cadena de filtros,
 * antes de llegar a los controladores: 401 sin autenticación válida y 403 sin permiso.
 * Los mensajes son fijos y no revelan si el usuario o el recurso existen.
 */
public final class SecurityErrorHandlers {

    private SecurityErrorHandlers() { }

    public static AuthenticationEntryPoint unauthorized() {
        return (request, response, exception) ->
                write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Autenticación requerida o token no válido");
    }

    public static AccessDeniedHandler forbidden() {
        return (request, response, exception) ->
                write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "No tienes permiso para realizar esta operación");
    }

    static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\","
                + "\"error\":\"" + message + "\",\"message\":\"" + message + "\"}");
    }
}

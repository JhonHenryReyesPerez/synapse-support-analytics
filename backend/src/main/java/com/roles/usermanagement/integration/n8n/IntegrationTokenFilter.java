package com.roles.usermanagement.integration.n8n;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica las llamadas de servicio de n8n a /api/integration/** con un secreto por entorno
 * (cabecera X-Integration-Token). Otorga únicamente ROLE_INTEGRATION: no es una cuenta de usuario
 * ni recibe permisos de administrador. Sin secreto configurado, esos endpoints quedan cerrados.
 */
@Component
public class IntegrationTokenFilter extends OncePerRequestFilter {

    public static final String PATH_PREFIX = "/api/integration/";
    public static final String TOKEN_HEADER = "X-Integration-Token";
    public static final String ROLE = "ROLE_INTEGRATION";

    private final N8nProperties properties;

    public IntegrationTokenFilter(N8nProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String expected = properties.inboundToken();
        String provided = request.getHeader(TOKEN_HEADER);
        if (expected != null && !expected.isBlank() && provided != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "n8n-service", null, List.of(new SimpleGrantedAuthority(ROLE))));
        }
        chain.doFilter(request, response);
    }
}

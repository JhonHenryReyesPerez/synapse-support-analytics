package com.roles.usermanagement.web.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.roles.usermanagement.domain.service.AccountUserDetails;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtFilter extends OncePerRequestFilter {

 /** Código estable para que el cliente reconozca el bloqueo por cambio de contraseña pendiente. */
 public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

 /** B03: únicas rutas disponibles para una cuenta con cambio de contraseña pendiente. */
 private static final Set<String> ALLOWED_WHILE_PASSWORD_CHANGE_PENDING = Set.of(
         "/api/auth/login",
         "/api/auth/change-password",
         "/api/auth/me"
 );

 private final JwtUtil jwt;
 private final UserDetailsService users;

 public JwtFilter(JwtUtil jwt, UserDetailsService users) {
  this.jwt = jwt;
  this.users = users;
 }

 /** Las rutas de integración solo admiten el secreto de servicio, no tokens de usuario. */
 @Override
 protected boolean shouldNotFilter(HttpServletRequest request) {
  return pathOf(request).startsWith("/api/integration/");
 }

 @Override
 protected void doFilterInternal(
         HttpServletRequest request,
         HttpServletResponse response,
         FilterChain chain
 ) throws ServletException, IOException {
  String header = request.getHeader(HttpHeaders.AUTHORIZATION);

  if (header != null && header.startsWith("Bearer ")) {
   String token = header.substring(7).trim();

   if (!token.isEmpty() && jwt.isValid(token)) {
    try {
     UserDetails user = users.loadUserByUsername(jwt.getUsername(token));

     if (user.isEnabled()
             && hasCurrentCredentials(user, jwt.getCredentialVersion(token))
             && user.isAccountNonLocked()
             && user.isAccountNonExpired()
             && user.isCredentialsNonExpired()) {
      if (user instanceof AccountUserDetails account
              && account.isMustChangePassword()
              && !ALLOWED_WHILE_PASSWORD_CHANGE_PENDING.contains(pathOf(request))) {
       rejectPasswordChangePending(response);
       return;
      }
      var authentication = new UsernamePasswordAuthenticationToken(
              user.getUsername(),
              null,
              user.getAuthorities()
      );
      authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(authentication);
     }
    } catch (UsernameNotFoundException ignored) {
     SecurityContextHolder.clearContext();
    }
   }
  }

  chain.doFilter(request, response);
 }

 /** B04: el token solo sirve si se emitió con la versión de credenciales vigente de la cuenta. */
 private static boolean hasCurrentCredentials(UserDetails user, Integer tokenVersion) {
  return user instanceof AccountUserDetails account
          && tokenVersion != null
          && tokenVersion == account.getCredentialVersion();
 }

 private static String pathOf(HttpServletRequest request) {
  return request.getRequestURI().substring(request.getContextPath().length());
 }

 private static void rejectPasswordChangePending(HttpServletResponse response) throws IOException {
  SecurityContextHolder.clearContext();
  SecurityErrorHandlers.write(response, HttpServletResponse.SC_FORBIDDEN, PASSWORD_CHANGE_REQUIRED,
          "Debes cambiar tu contraseña antes de continuar");
 }
}

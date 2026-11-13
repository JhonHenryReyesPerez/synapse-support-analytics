package com.roles.usermanagement.web.controller;

import com.roles.usermanagement.domain.dto.ChangePasswordDto;
import com.roles.usermanagement.domain.dto.LoginDto;
import com.roles.usermanagement.domain.dto.LoginResponseDto;
import com.roles.usermanagement.domain.dto.RegisterDto;
import com.roles.usermanagement.domain.dto.UserPermissionsDto;
import com.roles.usermanagement.domain.service.PasswordPolicy;
import com.roles.usermanagement.domain.service.UserService;
import com.roles.usermanagement.persistance.crud.UserCrudRepository;
import com.roles.usermanagement.persistance.entity.UserEntity;
import com.roles.usermanagement.persistance.repository.UserRepository;
import com.roles.usermanagement.web.config.JwtUtil;
import com.roles.usermanagement.web.config.LoginAttemptLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Registro, login, cambio de contraseña y sesión")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository users;
    private final UserCrudRepository userCrudRepository;
    private final UserService userService;
    private final LoginAttemptLimiter loginAttempts;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public AuthController(AuthenticationManager authenticationManager, 
                          JwtUtil jwtUtil, 
                          UserRepository users,
                          UserCrudRepository userCrudRepository,
                          UserService userService,
                          LoginAttemptLimiter loginAttempts) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.users = users;
        this.userCrudRepository = userCrudRepository;
        this.userService = userService;
        this.loginAttempts = loginAttempts;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar nuevo usuario", description = "Crea un usuario publico con rol USER por defecto")
    public ResponseEntity<?> register(@RequestBody RegisterDto registerDto) {
        if (registerDto.getUsername() == null || registerDto.getUsername().isBlank()
                || registerDto.getEmail() == null || registerDto.getEmail().isBlank()
                || registerDto.getPassword() == null || registerDto.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Todos los campos son obligatorios"));
        }

        if (!EMAIL_PATTERN.matcher(registerDto.getEmail().trim()).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El correo electrónico debe tener un formato válido"));
        }

        var passwordViolation = PasswordPolicy.violation(registerDto.getPassword());
        if (passwordViolation.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", passwordViolation.get()));
        }

        var created = userService.register(registerDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Usuario registrado exitosamente con rol USER",
                "username", created.getUsername(),
                "email", created.getEmail(),
                "role", created.getRole()
        ));
    }

    @PostMapping("/login")
    @Operation(summary = "Obtener token JWT", description = "Valida credenciales por usuario o correo y devuelve token junto a mustChangePassword. "
            + "Si mustChangePassword es true, el token solo permite /api/auth/change-password y /api/auth/me; "
            + "el resto de la API responde 403 con code PASSWORD_CHANGE_REQUIRED hasta cambiar la contraseña. "
            + "Tras varios intentos fallidos seguidos responde 429 con code TOO_MANY_LOGIN_ATTEMPTS y la cabecera Retry-After")
    public ResponseEntity<?> login(@RequestBody LoginDto loginDto) {
        String identifier = loginDto.getIdentifier();
        if (identifier == null || loginDto.getPassword() == null || loginDto.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Usuario/correo y contraseña son obligatorios"));
        }
        // §14: tras varios fallos seguidos el identificador se bloquea temporalmente, exista o no la cuenta.
        var retryAfter = loginAttempts.retryAfter(identifier);
        if (retryAfter.isPresent()) {
            long seconds = Math.max(1, retryAfter.get().toSeconds());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(org.springframework.http.HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                    .body(Map.of("error", "Demasiados intentos fallidos. Intenta de nuevo más tarde",
                            "code", "TOO_MANY_LOGIN_ATTEMPTS",
                            "retryAfterSeconds", seconds));
        }
        // B02: el login solo autentica; la política se aplica al ESTABLECER contraseñas.
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identifier, loginDto.getPassword()));

            UserEntity user = userCrudRepository.findById(authentication.getName())
                    .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Cuenta no disponible"));
            String token = jwtUtil.create(user.getUsername(), credentialVersion(user));
            boolean mustChange = Boolean.TRUE.equals(user.getMustChangePassword());
            loginAttempts.recordSuccess(identifier);

            return ResponseEntity.ok(new LoginResponseDto(token, authentication.getName(), mustChange));
        } catch (AuthenticationException exception) {
            loginAttempts.recordFailure(identifier);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales invalidas o cuenta no disponible"));
        }
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cambiar contraseña (primer ingreso o voluntario)",
            description = "Invalida todos los tokens emitidos antes del cambio y devuelve un token nuevo en el campo token")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordDto dto, Principal principal) {
        if (dto.getCurrentPassword() == null || dto.getCurrentPassword().isBlank()
                || dto.getNewPassword() == null || dto.getNewPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Contraseña actual y nueva son obligatorias"));
        }
        userService.changePassword(principal.getName(), dto.getCurrentPassword(), dto.getNewPassword());
        // B04: el cambio invalida todos los tokens anteriores; se entrega uno nuevo para continuar la sesión.
        UserEntity user = userCrudRepository.findById(principal.getName()).orElseThrow();
        return ResponseEntity.ok(Map.of(
                "message", "Contraseña actualizada exitosamente. Ya puedes utilizar la aplicación.",
                "token", jwtUtil.create(user.getUsername(), credentialVersion(user))));
    }

    private static int credentialVersion(UserEntity user) {
        return user.getCredentialVersion() == null ? 0 : user.getCredentialVersion();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Consultar rol y permisos de la sesión actual")
    public UserPermissionsDto me(Principal principal) {
        return users.permissionDetails(principal.getName());
    }
}

package com.roles.usermanagement.web.controller;

import com.roles.usermanagement.domain.dto.*;
import com.roles.usermanagement.domain.service.UserService;
import com.roles.usermanagement.web.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@Tag(name="Usuarios", description="Cada operación requiere su permiso específico")
@SecurityRequirement(name=OpenApiConfig.BEARER_AUTH)
public class UserController {
    private final UserService users;
    public UserController(UserService users) {this.users=users;}

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary="Listar usuarios")
    public ResponseEntity<List<UserDto>> all() {return ResponseEntity.ok(users.getAllUsers());}

    @PostMapping("/add")
    @PreAuthorize("hasAuthority('USER_CREATE') and ((#dto.role == null and (#dto.roles == null or #dto.roles.isEmpty())) or hasAuthority('ROLE_ASSIGN')) and (#dto.additionalPermissions == null or #dto.additionalPermissions.isEmpty() or hasAuthority('PERMISSION_ASSIGN'))")
    @Operation(summary="Crear un usuario", description="USER por defecto. Un rol distinto o permisos adicionales exigen además rol ADMIN.")
    public ResponseEntity<UserDto> add(@RequestBody UserDto dto, Authentication actor) {return ResponseEntity.ok(users.saveUser(dto, actor));}

    @PutMapping("/update")
    @PreAuthorize("hasAuthority('USER_UPDATE') and ((#dto.role == null and #dto.roles == null) or hasAuthority('ROLE_ASSIGN')) and (#dto.additionalPermissions == null or hasAuthority('PERMISSION_ASSIGN'))")
    @Operation(summary="Actualizar un usuario", description="Campos omitidos se conservan. role cambia el rol único. additionalPermissions reemplaza los permisos individuales. password se recibe en texto.")
    public ResponseEntity<UserDto> update(@RequestBody UserDto dto, Authentication actor) {return ResponseEntity.ok(users.updateUser(dto, actor));}

    @PostMapping("/assignRole")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    @Operation(summary="Cambiar el rol único del usuario", description="Reemplaza el rol anterior. Conserva los permisos individuales.")
    public ResponseEntity<UserRoleDto> assignRole(@RequestBody UserRoleDto dto, Authentication actor) {return ResponseEntity.ok(users.assignRoleToUser(dto, actor));}

    @PostMapping("/assignPermission")
    @PreAuthorize("hasAuthority('PERMISSION_ASSIGN')")
    @Operation(summary="Agregar un permiso individual", description="Se suma a los permisos del rol sin cambiarlo. Repetir la asignación no crea duplicados.")
    public ResponseEntity<UserDto> grant(@RequestBody UserPermissionDto dto, Authentication actor) {return ResponseEntity.ok(users.grantPermission(dto, actor));}

    @DeleteMapping("/{username}/permissions/{permission}")
    @PreAuthorize("hasAuthority('PERMISSION_ASSIGN')")
    @Operation(summary="Retirar un permiso individual", description="Los permisos heredados del rol permanecen.")
    public ResponseEntity<UserDto> revoke(@PathVariable String username,@PathVariable String permission, Authentication actor) {
        return ResponseEntity.ok(users.revokePermission(username,permission,actor));
    }
    @GetMapping("/{username}/permissions")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary="Consultar permisos heredados individuales y efectivos")
    public ResponseEntity<UserPermissionsDto> details(@PathVariable String username) {return ResponseEntity.ok(users.permissionDetails(username));}

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('PERMISSION_ASSIGN')")
    @Operation(summary="Listar el catálogo de permisos")
    public ResponseEntity<List<String>> catalog() {return ResponseEntity.ok(users.permissionCatalog());}

    @DeleteMapping("/delete/{name}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    @Operation(summary="Dar de baja un usuario",
            description="Baja lógica: deshabilita la cuenta e invalida sus tokens. No se elimina porque incidentes e historial la referencian.")
    public ResponseEntity<Void> delete(@PathVariable String name, Authentication actor) {
        users.deleteUser(name, actor); return ResponseEntity.ok().build();
    }

    @PostMapping("/{username}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Super Admin restablece la contraseña de un usuario", 
               description = "Asigna la clave temporal indicada o, si se omite, una aleatoria. Obliga a cambiarla en el siguiente login e invalida los tokens anteriores del usuario")
    public ResponseEntity<?> resetUserPassword(
            @PathVariable String username,
            @RequestBody(required = false) Map<String, String> body,
            Authentication actor) {
        
        String tempPass = (body != null) ? body.get("temporaryPassword") : null;
        if (tempPass == null || tempPass.isBlank()) {
            // Sin clave indicada se genera una aleatoria; no existe contraseña temporal fija.
            tempPass = com.roles.usermanagement.domain.service.TemporaryPasswordGenerator.generate();
        }

        users.adminResetPassword(username, tempPass, actor);

        return ResponseEntity.ok(Map.of(
                "message", "Contraseña restablecida con éxito para el usuario: " + username,
                "temporaryPassword", tempPass,
                "mustChangePassword", true
        ));
    }
}

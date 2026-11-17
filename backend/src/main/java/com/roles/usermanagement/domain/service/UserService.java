package com.roles.usermanagement.domain.service;

import com.roles.usermanagement.domain.dto.RegisterDto;
import com.roles.usermanagement.domain.dto.UserDto;
import com.roles.usermanagement.domain.dto.UserPermissionDto;
import com.roles.usermanagement.domain.dto.UserPermissionsDto;
import com.roles.usermanagement.domain.dto.UserRoleDto;
import com.roles.usermanagement.persistance.crud.UserCrudRepository;
import com.roles.usermanagement.persistance.entity.UserEntity;
import com.roles.usermanagement.persistance.repository.UserRepository;
import com.roles.usermanagement.persistance.repository.UserRoleRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Casos de uso de cuentas. Las reglas de negocio (quién puede hacer qué y cuándo) viven aquí y en
 * {@link UserAccountPolicy}; {@link UserRepository} conserva la validación de datos y la persistencia (B10).
 * Cada operación es una sola transacción: la comprobación y el cambio se confirman juntos.
 */
@Service
public class UserService {

  private final UserRepository userRepository;
  private final UserRoleRepository userRoleRepository;
  private final UserCrudRepository accounts;
  private final UserAccountPolicy policy;

  public UserService(UserRepository userRepository, UserRoleRepository userRoleRepository,
      UserCrudRepository accounts, UserAccountPolicy policy) {
    this.userRepository = userRepository;
    this.userRoleRepository = userRoleRepository;
    this.accounts = accounts;
    this.policy = policy;
  }

  @Transactional
  public UserDto saveUser(UserDto dto, Authentication actor) {
    boolean elevatedRole = requestedRole(dto) != null && !"USER".equals(requestedRole(dto));
    boolean withPermissions = dto.getAdditionalPermissions() != null && !dto.getAdditionalPermissions().isEmpty();
    if (elevatedRole || withPermissions) {
      policy.requireAdmin(actor, "crear cuentas con rol distinto de USER o con permisos adicionales");
    }
    return userRepository.save(dto);
  }

  @Transactional
  public UserDto updateUser(UserDto dto, Authentication actor) {
    UserEntity target = lockedAccount(dto.getUsername());
    policy.requireCanModify(target, actor);

    String newRole = requestedRole(dto);
    boolean roleChanges = newRole != null && !UserAccountPolicy.hasRole(target, newRole);
    if (roleChanges || dto.getRoles() != null || dto.getAdditionalPermissions() != null) {
      policy.requireAdmin(actor, "cambiar roles o permisos");
    }
    if (dto.getPassword() != null) {
      policy.requireAdmin(actor, "establecer la contraseña de otra cuenta");
    }
    boolean deactivates = UserAccountPolicy.isActive(target)
        && (Boolean.TRUE.equals(dto.getDisabled()) || Boolean.TRUE.equals(dto.getLocked()));
    if (roleChanges) {
      policy.requireCanLeaveRoleOrDeactivate(target, "cambiar el rol");
    } else if (deactivates) {
      policy.requireCanLeaveRoleOrDeactivate(target, "deshabilitar o bloquear la cuenta");
    }
    return userRepository.update(dto);
  }

  @Transactional
  public UserRoleDto assignRoleToUser(UserRoleDto dto, Authentication actor) {
    policy.requireAdmin(actor, "cambiar roles");
    UserEntity target = lockedAccount(dto.getUsername());
    if (dto.getRole() != null && !UserAccountPolicy.hasRole(target, dto.getRole())) {
      policy.requireCanLeaveRoleOrDeactivate(target, "cambiar el rol");
    }
    return userRoleRepository.save(dto);
  }

  @Transactional
  public UserDto grantPermission(UserPermissionDto dto, Authentication actor) {
    policy.requireAdmin(actor, "asignar permisos");
    return userRepository.grantPermission(dto);
  }

  @Transactional
  public UserDto revokePermission(String username, String permission, Authentication actor) {
    policy.requireAdmin(actor, "retirar permisos");
    return userRepository.revokePermission(username, permission);
  }

  /**
   * Baja lógica: la cuenta se deshabilita y sus tokens dejan de servir. No se elimina físicamente
   * porque incidentes, historial y auditorías la referencian.
   */
  @Transactional
  public void deleteUser(String username, Authentication actor) {
    UserEntity target = lockedAccount(username);
    policy.requireCanModify(target, actor);
    if (actor != null && username.equals(actor.getName())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes dar de baja tu propia cuenta");
    }
    if (!Boolean.TRUE.equals(target.getDisabled())) {
      policy.requireCanLeaveRoleOrDeactivate(target, "dar de baja la cuenta");
    }
    userRepository.disable(username);
  }

  @Transactional
  public void adminResetPassword(String targetUsername, String temporaryPassword, Authentication actor) {
    policy.requireAdmin(actor, "restablecer contraseñas");
    userRepository.adminResetPassword(targetUsername, temporaryPassword);
  }

  public List<UserDto> getAllUsers() {
    return userRepository.getAllUsers();
  }

  public UserPermissionsDto permissionDetails(String username) {
    return userRepository.permissionDetails(username);
  }

  public List<String> permissionCatalog() {
    return userRepository.permissionCatalog();
  }

  public void changePassword(String username, String currentPassword, String newPassword) {
    userRepository.changeUserPassword(username, currentPassword, newPassword);
  }

  /** Autorregistro: crea únicamente cuentas USER. */
  public UserDto register(RegisterDto dto) {
    return userRepository.register(dto);
  }

  private UserEntity lockedAccount(String username) {
    if (username == null || username.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario es obligatorio");
    }
    // Orden único de bloqueo: primero el conjunto de administradores activos y después la cuenta.
    // UserAccountPolicy vuelve a pedir ese conjunto; si aquí se bloqueara antes la cuenta, dos
    // operaciones sobre administradores distintos se esperarían mutuamente (interbloqueo).
    accounts.lockActiveByRole("ADMIN");
    return accounts.findForUpdate(username)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario no existe"));
  }

  private static String requestedRole(UserDto dto) {
    if (dto.getRole() != null) {
      return dto.getRole();
    }
    if (dto.getRoles() != null && dto.getRoles().size() == 1 && dto.getRoles().get(0) != null) {
      return dto.getRoles().get(0).getRole();
    }
    return null;
  }
}

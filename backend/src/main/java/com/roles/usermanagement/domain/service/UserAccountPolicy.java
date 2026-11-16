package com.roles.usermanagement.domain.service;

import com.roles.usermanagement.modules.lifecycle.enums.TicketStatus;
import com.roles.usermanagement.modules.ticket.repository.TicketRepository;
import com.roles.usermanagement.persistance.crud.UserCrudRepository;
import com.roles.usermanagement.persistance.entity.UserEntity;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Política efectiva única de gestión de cuentas (B12, §5).
 *
 * El rol delimita lo que un usuario puede hacer; un permiso individual solo habilita operaciones
 * dentro de ese límite. Por eso las operaciones que permitirían ascender privilegios —asignar
 * roles o permisos, establecer contraseñas ajenas o tocar una cuenta ADMIN— exigen rol ADMIN
 * aunque el actor tenga el permiso individual correspondiente.
 */
@Component
public class UserAccountPolicy {

    private static final List<TicketStatus> ACTIVE_STATUSES = List.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS);

    private final UserCrudRepository users;
    private final TicketRepository tickets;

    public UserAccountPolicy(UserCrudRepository users, TicketRepository tickets) {
        this.users = users;
        this.tickets = tickets;
    }

    public boolean isAdmin(Authentication actor) {
        return actor != null && actor.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    /** Operación reservada al rol ADMIN con independencia de los permisos individuales. */
    public void requireAdmin(Authentication actor, String operation) {
        if (!isAdmin(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un ADMIN puede " + operation);
        }
    }

    /** Una cuenta ADMIN solo puede ser modificada por otro ADMIN. */
    public void requireCanModify(UserEntity target, Authentication actor) {
        if (hasRole(target, "ADMIN")) {
            requireAdmin(actor, "modificar una cuenta de administrador");
        }
    }

    /**
     * Comprueba que la cuenta pueda dejar su rol actual o quedar inactiva:
     * no se deja al sistema sin administrador activo ni a un técnico con tickets activos sin responsable.
     */
    public void requireCanLeaveRoleOrDeactivate(UserEntity target, String motive) {
        if (hasRole(target, "ADMIN") && isActive(target)) {
            // Con bloqueo: sin él, dos administradores que se degradan a la vez cuentan 2 y dejan 0.
            users.lockActiveByRole("ADMIN");
            if (users.countActiveByRole("ADMIN") <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "No se puede " + motive + ": es el único administrador activo del sistema");
            }
        }
        if (hasRole(target, "TECHNICIAN")
                && tickets.countByTechnicianUsernameAndStatusIn(target.getUsername(), ACTIVE_STATUSES) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede " + motive + ": el técnico tiene tickets activos; resuélvelos o reasígnalos primero");
        }
    }

    public static boolean hasRole(UserEntity user, String role) {
        return user.getRoles() != null && user.getRoles().stream().anyMatch(item -> role.equals(item.getRole()));
    }

    public static boolean isActive(UserEntity user) {
        return !Boolean.TRUE.equals(user.getDisabled()) && !Boolean.TRUE.equals(user.getLocked());
    }
}

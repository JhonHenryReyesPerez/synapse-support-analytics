package com.roles.usermanagement.domain.service;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * UserDetails de la cuenta con el indicador de cambio de contraseña pendiente (B03).
 * Permite que el filtro JWT restrinja la sesión sin una consulta adicional.
 */
public class AccountUserDetails extends User {

    private final boolean mustChangePassword;

    public AccountUserDetails(String username, String password, boolean enabled, boolean accountNonLocked,
                              Collection<? extends GrantedAuthority> authorities, boolean mustChangePassword) {
        super(username, password, enabled, true, true, accountNonLocked, authorities);
        this.mustChangePassword = mustChangePassword;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }
}

package com.roles.usermanagement.domain.service;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * UserDetails de la cuenta con el indicador de cambio pendiente (B03) y la versión de credenciales (B04).
 * Permite que el filtro JWT restrinja la sesión sin una consulta adicional.
 */
public class AccountUserDetails extends User {

    private final boolean mustChangePassword;
    private final int credentialVersion;

    public AccountUserDetails(String username, String password, boolean enabled, boolean accountNonLocked,
                              Collection<? extends GrantedAuthority> authorities, boolean mustChangePassword,
                              int credentialVersion) {
        super(username, password, enabled, true, true, accountNonLocked, authorities);
        this.mustChangePassword = mustChangePassword;
        this.credentialVersion = credentialVersion;
    }

    /** Versión de credenciales vigente de la cuenta (B04). */
    public int getCredentialVersion() {
        return credentialVersion;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }
}

package com.roles.usermanagement.domain.service;

import com.roles.usermanagement.domain.dto.UserDto;
import com.roles.usermanagement.persistance.crud.RoleCrudRepository;
import com.roles.usermanagement.persistance.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserSecurityService implements UserDetailsService {
    public static final String RANDOM_ORDER = "random_order";
    public static final String ROLE_PREFIX = "ROLE_";
    private final UserRepository userRepository;
    private final RoleCrudRepository roleRepository;

    public UserSecurityService(UserRepository userRepository, RoleCrudRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserDto user = userRepository.loadUserByUsername(username);
        if (user == null) throw new UsernameNotFoundException("User " + username + " not found.");
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        if (user.getRoles() != null) {
            user.getRoles().forEach(assignment -> {
                authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + assignment.getRole()));
                roleRepository.findById(assignment.getRole()).ifPresent(role ->
                        role.getPermissions().forEach(permission ->
                                authorities.add(new SimpleGrantedAuthority(permission.getName()))));
            });
        }
        if (user.getAdditionalPermissions() != null) {
            user.getAdditionalPermissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        }
        return new AccountUserDetails(user.getUsername(), user.getPassword(),
                !Boolean.TRUE.equals(user.getDisabled()), !Boolean.TRUE.equals(user.getLocked()),
                authorities, Boolean.TRUE.equals(user.getMustChangePassword()));
    }
}

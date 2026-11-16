package com.roles.usermanagement.persistance.crud;

import com.roles.usermanagement.persistance.entity.UserEntity;
import org.springframework.data.repository.CrudRepository;

/**
 * Interface that defines CRUD operations (Create, Read, Update, Delete) for the UserEntity entity.
 * Extends CrudRepository provided by Spring Data JPA.
 *
 * Allows performing basic persistence operations for UserEntity objects in the database.
 */
public interface UserCrudRepository extends CrudRepository<UserEntity, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserEntity u where u.username = :username")
    java.util.Optional<UserEntity> findForUpdate(@org.springframework.data.repository.query.Param("username") String username);

    boolean existsByEmailAndUsernameNot(String email, String username);

    boolean existsByEmail(String email);

    /** Cuentas habilitadas y sin bloqueo que tienen el rol indicado. */
    @org.springframework.data.jpa.repository.Query("""
            select count(u) from UserEntity u join u.roles r
            where r.role = :role and u.disabled = false and u.locked = false
            """)
    long countActiveByRole(@org.springframework.data.repository.query.Param("role") String role);

    /** Bloquea las cuentas activas del rol para que dos cambios simultáneos se evalúen uno tras otro. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
            select u from UserEntity u
            where u.disabled = false and u.locked = false
              and u.username in (select r.username from UserRoleEntity r where r.role = :role)
            order by u.username
            """)
    java.util.List<UserEntity> lockActiveByRole(@org.springframework.data.repository.query.Param("role") String role);

    java.util.Optional<UserEntity> findByEmail(String email);
}

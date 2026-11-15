package com.roles.usermanagement.modules.assistance.repository;

import com.roles.usermanagement.modules.assistance.entity.AssistanceQuery;
import com.roles.usermanagement.modules.assistance.enums.AssistanceStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistanceQueryRepository extends JpaRepository<AssistanceQuery, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from AssistanceQuery q where q.id = :id")
    Optional<AssistanceQuery> findForUpdate(@Param("id") Long id);

    Optional<AssistanceQuery> findByRequestId(String requestId);

    boolean existsByTicketIdAndRequesterUsernameAndStatus(Long ticketId, String username, AssistanceStatus status);

    Page<AssistanceQuery> findByTicketId(Long ticketId, Pageable pageable);

    /**
     * Cierra como ERROR las consultas PENDING creadas antes del límite. La condición sobre el estado
     * forma parte de la sentencia: una consulta que ya terminó nunca se sobrescribe. No limpia el
     * contexto de persistencia: quien la invoque no debe tener cargadas consultas PENDING.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update AssistanceQuery q
            set q.status = com.roles.usermanagement.modules.assistance.enums.AssistanceStatus.ERROR,
                q.errorCode = :errorCode, q.completedAt = :now
            where q.status = com.roles.usermanagement.modules.assistance.enums.AssistanceStatus.PENDING
              and q.createdAt < :cutoff
            """)
    int expirePending(@Param("cutoff") LocalDateTime cutoff, @Param("errorCode") String errorCode,
                      @Param("now") LocalDateTime now);
}

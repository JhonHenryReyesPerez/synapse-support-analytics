package com.roles.usermanagement.modules.ticket.repository;

import com.roles.usermanagement.modules.ticket.entity.Ticket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// JpaRepository -> CRUD // JpaSpecification -> queries with programmatic filters
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findForUpdate(@Param("id") Long id);

    /** Carga por técnico: número de tickets en los estados indicados. Columnas: username, count. */
    @Query("""
            select t.technician.username, count(t) from Ticket t
            where t.technician is not null and t.status in :statuses
            group by t.technician.username
            """)
    java.util.List<Object[]> countByTechnicianAndStatusIn(
            @Param("statuses") java.util.Collection<com.roles.usermanagement.modules.lifecycle.enums.TicketStatus> statuses);
}

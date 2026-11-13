package com.roles.usermanagement.modules.assistance.repository;

import com.roles.usermanagement.modules.assistance.entity.AiQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiQueryRepository extends JpaRepository<AiQuery, Long> {
    List<AiQuery> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
}

package com.roles.usermanagement.modules.assistance.repository;

import com.roles.usermanagement.modules.assistance.entity.AiResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiResponseRepository extends JpaRepository<AiResponse, Long> {
    Optional<AiResponse> findByQueryId(Long queryId);
}

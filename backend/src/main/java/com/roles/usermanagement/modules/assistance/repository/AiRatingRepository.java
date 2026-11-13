package com.roles.usermanagement.modules.assistance.repository;

import com.roles.usermanagement.modules.assistance.entity.AiRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiRatingRepository extends JpaRepository<AiRating, Long> {
    Optional<AiRating> findByResponseId(Long responseId);
}

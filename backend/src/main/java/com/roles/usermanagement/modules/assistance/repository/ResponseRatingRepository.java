package com.roles.usermanagement.modules.assistance.repository;

import com.roles.usermanagement.modules.assistance.entity.ResponseRating;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResponseRatingRepository extends JpaRepository<ResponseRating, Long> {

    Optional<ResponseRating> findByResponseIdAndUserUsername(Long responseId, String username);

    List<ResponseRating> findByResponseIdIn(Collection<Long> responseIds);
}

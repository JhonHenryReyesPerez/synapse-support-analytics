package com.roles.usermanagement.modules.assistance.service;

import com.roles.usermanagement.modules.assistance.dto.RateResponseRequest;
import com.roles.usermanagement.modules.assistance.dto.RatingDto;
import com.roles.usermanagement.modules.assistance.entity.AiRating;
import com.roles.usermanagement.modules.assistance.entity.AiResponse;
import com.roles.usermanagement.modules.assistance.repository.AiRatingRepository;
import com.roles.usermanagement.modules.assistance.repository.AiResponseRepository;
import com.roles.usermanagement.persistance.crud.UserCrudRepository;
import com.roles.usermanagement.persistance.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatingService {

    private final AiRatingRepository ratingRepository;
    private final AiResponseRepository responseRepository;
    private final UserCrudRepository userRepository;

    @Transactional
    public RatingDto rateResponse(Long responseId, RateResponseRequest request, String username) {
        AiResponse response = responseRepository.findById(responseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Respuesta de IA no encontrada"));

        UserEntity user = userRepository.findById(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no válido"));

        if (!response.getQuery().getRequestedBy().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el usuario que solicitó la consulta puede calificar la respuesta");
        }

        AiRating rating = ratingRepository.findByResponseId(responseId).orElse(null);
        if (rating == null) {
            rating = AiRating.builder()
                    .response(response)
                    .ratedBy(user)
                    .stars(request.stars())
                    .comment(request.comment() != null ? request.comment().trim() : null)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        } else {
            rating.setStars(request.stars());
            rating.setComment(request.comment() != null ? request.comment().trim() : null);
            rating.setUpdatedAt(LocalDateTime.now());
        }

        AiRating saved = ratingRepository.save(rating);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public RatingDto getRating(Long responseId) {
        AiRating rating = ratingRepository.findByResponseId(responseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Calificación no encontrada para esta respuesta"));
        return toDto(rating);
    }

    private RatingDto toDto(AiRating r) {
        return new RatingDto(
                r.getId(),
                r.getResponse().getId(),
                r.getRatedBy().getUsername(),
                r.getStars(),
                r.getComment(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}

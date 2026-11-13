package com.roles.usermanagement.modules.assistance.service;

import com.roles.usermanagement.modules.assistance.dto.RatingRequest;
import com.roles.usermanagement.modules.assistance.dto.RatingResponse;
import com.roles.usermanagement.modules.assistance.entity.AssistanceQuery;
import com.roles.usermanagement.modules.assistance.entity.AssistanceResponse;
import com.roles.usermanagement.modules.assistance.entity.ResponseRating;
import com.roles.usermanagement.modules.assistance.enums.AssistanceStatus;
import com.roles.usermanagement.modules.assistance.repository.AssistanceQueryRepository;
import com.roles.usermanagement.modules.assistance.repository.AssistanceResponseRepository;
import com.roles.usermanagement.modules.assistance.repository.ResponseRatingRepository;
import com.roles.usermanagement.modules.ticket.service.TicketAccessPolicy;
import com.roles.usermanagement.persistance.crud.UserCrudRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Evaluación de respuestas (RF08). Solo quien hizo una consulta respondida puede calificar su
 * respuesta, sea cual sea su rol; ADMIN no califica consultas ajenas. La calificación mide la
 * utilidad percibida: no verifica artículos ni cambia similitudes.
 */
@Service
public class RatingService {

    private final AssistanceQueryRepository queries;
    private final AssistanceResponseRepository responses;
    private final ResponseRatingRepository ratings;
    private final UserCrudRepository users;
    private final TicketAccessPolicy policy;

    public RatingService(AssistanceQueryRepository queries, AssistanceResponseRepository responses,
                         ResponseRatingRepository ratings, UserCrudRepository users, TicketAccessPolicy policy) {
        this.queries = queries;
        this.responses = responses;
        this.ratings = ratings;
        this.users = users;
        this.policy = policy;
    }

    /** Crea la calificación o actualiza la existente: nunca hay dos del mismo usuario para una respuesta. */
    @Transactional
    public RatingResponse rate(Long queryId, RatingRequest request, Authentication auth) {
        // El bloqueo de la consulta serializa dos calificaciones simultáneas del mismo usuario.
        AssistanceQuery query = queries.findForUpdate(queryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La consulta no existe"));
        policy.assertCanView(query.getTicket(), auth);
        if (!query.getRequester().getUsername().equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo quien hizo la consulta puede calificar su respuesta");
        }
        if (query.getStatus() != AssistanceStatus.ANSWERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se pueden calificar consultas respondidas");
        }
        AssistanceResponse response = responses.findByQueryId(queryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "La consulta no tiene una respuesta que calificar"));

        // Precisión de la columna (microsegundos): la respuesta coincide con lo que queda guardado.
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        ResponseRating rating = ratings.findByResponseIdAndUserUsername(response.getId(), auth.getName()).orElse(null);
        if (rating == null) {
            rating = new ResponseRating();
            rating.setResponse(response);
            rating.setUser(users.findById(auth.getName())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario no existe")));
            rating.setCreatedAt(now);
        }
        rating.setScore(request.score().intValueExact());
        String comment = request.comment() == null ? "" : request.comment().trim();
        rating.setComment(comment.isEmpty() ? null : comment);
        rating.setUpdatedAt(now);
        return toResponse(queryId, ratings.save(rating));
    }

    static RatingResponse toResponse(Long queryId, ResponseRating rating) {
        return new RatingResponse(queryId, rating.getScore(), rating.getComment(), rating.getUser().getUsername(),
                rating.getCreatedAt(), rating.getUpdatedAt());
    }
}

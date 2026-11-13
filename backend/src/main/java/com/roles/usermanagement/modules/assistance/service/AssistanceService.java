package com.roles.usermanagement.modules.assistance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roles.usermanagement.integration.n8n.N8nClient;
import com.roles.usermanagement.integration.n8n.dto.AssistRequest;
import com.roles.usermanagement.integration.n8n.dto.AssistResponse;
import com.roles.usermanagement.modules.assistance.dto.AiQueryDto;
import com.roles.usermanagement.modules.assistance.dto.AiResponseDto;
import com.roles.usermanagement.modules.assistance.dto.AiSourceDto;
import com.roles.usermanagement.modules.assistance.dto.AskAssistanceRequest;
import com.roles.usermanagement.modules.assistance.entity.AiQuery;
import com.roles.usermanagement.modules.assistance.entity.AiResponse;
import com.roles.usermanagement.modules.assistance.entity.AiSource;
import com.roles.usermanagement.modules.assistance.enums.AiQueryStatus;
import com.roles.usermanagement.modules.assistance.repository.AiQueryRepository;
import com.roles.usermanagement.modules.assistance.repository.AiResponseRepository;
import com.roles.usermanagement.modules.knowledge.entity.KnowledgeArticle;
import com.roles.usermanagement.modules.knowledge.repository.KnowledgeArticleRepository;
import com.roles.usermanagement.modules.lifecycle.enums.TicketStatus;
import com.roles.usermanagement.modules.ticket.entity.Ticket;
import com.roles.usermanagement.modules.ticket.repository.TicketRepository;
import com.roles.usermanagement.persistance.crud.UserCrudRepository;
import com.roles.usermanagement.persistance.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssistanceService {

    private final TicketRepository ticketRepository;
    private final UserCrudRepository userRepository;
    private final KnowledgeArticleRepository articleRepository;
    private final AiQueryRepository queryRepository;
    private final AiResponseRepository responseRepository;
    private final N8nClient n8nClient;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private record TicketSnapshot(
            Long id,
            String title,
            String description,
            String categoryName,
            String requesterUsername,
            String assignedTechUsername,
            TicketStatus status
    ) {}

    public AiQueryDto askAssistance(Long ticketId, AskAssistanceRequest request, String username) {
        TicketSnapshot snapshot = transactions.execute(status -> {
            Ticket ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket no encontrado"));

            if (ticket.getStatus() == TicketStatus.CLOSED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede solicitar asistencia para un ticket CERRADO");
            }

            UserEntity user = userRepository.findById(username)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no válido"));

            boolean isAdmin = user.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getRole()));
            boolean isOwner = ticket.getApplicant() != null && ticket.getApplicant().getUsername().equals(username);
            boolean isAssigned = ticket.getTechnician() != null && ticket.getTechnician().getUsername().equals(username);

            if (!isAdmin && !isOwner && !isAssigned) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para solicitar asistencia sobre este ticket");
            }

            String catName = ticket.getCategory() != null ? ticket.getCategory().getName() : "General";
            String reqUser = ticket.getApplicant() != null ? ticket.getApplicant().getUsername() : null;
            String techUser = ticket.getTechnician() != null ? ticket.getTechnician().getUsername() : null;

            return new TicketSnapshot(ticket.getId(), ticket.getTitle(), ticket.getDescription(), catName, reqUser, techUser, ticket.getStatus());
        });

        String prompt = (request != null && request.prompt() != null && !request.prompt().isBlank())
                ? request.prompt().trim()
                : snapshot.title() + ". " + (snapshot.description() != null ? snapshot.description() : "");

        Long queryId = transactions.execute(status -> {
            Ticket ticketRef = ticketRepository.getReferenceById(snapshot.id());
            UserEntity userRef = userRepository.findById(username).orElseThrow();

            AiQuery q = AiQuery.builder()
                    .ticket(ticketRef)
                    .requestedBy(userRef)
                    .queryText(prompt)
                    .status(AiQueryStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .build();
            return queryRepository.save(q).getId();
        });

        AssistRequest n8nRequest = new AssistRequest(
                1,
                UUID.randomUUID().toString(),
                queryId,
                snapshot.id(),
                1,
                prompt,
                snapshot.title(),
                snapshot.description(),
                snapshot.categoryName(),
                3,
                0.60
        );

        long start = System.currentTimeMillis();
        AssistResponse n8nResponse;
        try {
            n8nResponse = n8nClient.assist(n8nRequest);
        } catch (Exception e) {
            log.error("Error al comunicarse con n8n para asistencia RAG: {}", e.getMessage());
            markQueryError(queryId, "Error en motor de asistencia IA: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al comunicarse con el asistente de IA", e);
        }
        long durationMs = System.currentTimeMillis() - start;

        if (n8nResponse == null) {
            markQueryError(queryId, "Respuesta nula recibida del asistente");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Respuesta vacía del asistente IA");
        }

        if ("NO_RESULTS".equalsIgnoreCase(n8nResponse.status())) {
            return saveNoResults(queryId);
        }

        if ("ERROR".equalsIgnoreCase(n8nResponse.status())) {
            String err = n8nResponse.errorCode() != null ? n8nResponse.errorCode() : "Error reportado por el motor de IA";
            markQueryError(queryId, err);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "El asistente reportó un error: " + err);
        }

        boolean validSources = validateSourcesEligibility(n8nResponse.sources());
        if (!validSources) {
            log.warn("Fuentes de n8n no pasaron validación de elegibilidad relacional. Intentando reintento único...");
            try {
                AssistRequest retryReq = new AssistRequest(
                        1,
                        UUID.randomUUID().toString(),
                        queryId,
                        snapshot.id(),
                        2,
                        prompt,
                        snapshot.title(),
                        snapshot.description(),
                        snapshot.categoryName(),
                        3,
                        0.60
                );
                n8nResponse = n8nClient.assist(retryReq);
                if (n8nResponse == null || "NO_RESULTS".equalsIgnoreCase(n8nResponse.status())) {
                    return saveNoResults(queryId);
                }
                if (!validateSourcesEligibility(n8nResponse.sources())) {
                    log.warn("Reintento falló validación de fuentes. Marcando NO_RESULTS.");
                    return saveNoResults(queryId);
                }
            } catch (Exception ex) {
                return saveNoResults(queryId);
            }
        }

        AssistResponse finalResp = n8nResponse;
        return transactions.execute(status -> {
            AiQuery q = queryRepository.findById(queryId).orElseThrow();
            q.setStatus(AiQueryStatus.ANSWERED);
            queryRepository.save(q);

            String stepsJson = null;
            if (finalResp.recommendedSteps() != null) {
                try {
                    stepsJson = objectMapper.writeValueAsString(finalResp.recommendedSteps());
                } catch (Exception ignore) {}
            }

            AiResponse respEntity = AiResponse.builder()
                    .query(q)
                    .summary(finalResp.answer())
                    .stepsJson(stepsJson)
                    .recommendations(null)
                    .sourcesCount(finalResp.sources() != null ? finalResp.sources().size() : 0)
                    .modelName(finalResp.generationModel() != null ? finalResp.generationModel() : "gemini-2.5-flash")
                    .responseTimeMs(durationMs)
                    .createdAt(LocalDateTime.now())
                    .sources(new ArrayList<>())
                    .build();

            if (finalResp.sources() != null) {
                for (AssistResponse.Source src : finalResp.sources()) {
                    KnowledgeArticle art = articleRepository.findById(src.articleId()).orElse(null);
                    if (art != null) {
                        AiSource sourceEntity = AiSource.builder()
                                .response(respEntity)
                                .article(art)
                                .articleTitle(art.getTitle())
                                .similarityScore(src.score() != null ? src.score() : 1.0)
                                .versionUsed(src.contentVersion() != null ? src.contentVersion() : art.getContentVersion())
                                .build();
                        respEntity.getSources().add(sourceEntity);
                    }
                }
            }

            AiResponse savedResp = responseRepository.save(respEntity);
            return toDto(q, savedResp);
        });
    }

    public AiQueryDto getQuery(Long queryId, String username) {
        return transactions.execute(status -> {
            AiQuery q = queryRepository.findById(queryId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consulta de IA no encontrada"));

            UserEntity user = userRepository.findById(username)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no válido"));

            boolean isAdmin = user.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getRole()));
            boolean isRequester = q.getRequestedBy().getUsername().equals(username);
            boolean isTicketOwner = q.getTicket().getApplicant() != null && q.getTicket().getApplicant().getUsername().equals(username);
            boolean isAssignedTech = q.getTicket().getTechnician() != null && q.getTicket().getTechnician().getUsername().equals(username);

            if (!isAdmin && !isRequester && !isTicketOwner && !isAssignedTech) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para ver esta consulta");
            }

            return toDto(q, q.getResponse());
        });
    }

    private boolean validateSourcesEligibility(List<AssistResponse.Source> sources) {
        if (sources == null || sources.isEmpty()) {
            return false;
        }
        for (AssistResponse.Source ref : sources) {
            KnowledgeArticle art = articleRepository.findById(ref.articleId()).orElse(null);
            if (art == null || !art.isActive() || !art.isVerified()) {
                return false;
            }
        }
        return true;
    }

    private void markQueryError(Long queryId, String error) {
        transactions.execute(status -> {
            AiQuery q = queryRepository.findById(queryId).orElse(null);
            if (q != null) {
                q.setStatus(AiQueryStatus.ERROR);
                q.setErrorMessage(error);
                queryRepository.save(q);
            }
            return null;
        });
    }

    private AiQueryDto saveNoResults(Long queryId) {
        return transactions.execute(status -> {
            AiQuery q = queryRepository.findById(queryId).orElseThrow();
            q.setStatus(AiQueryStatus.NO_RESULTS);
            queryRepository.save(q);
            return toDto(q, null);
        });
    }

    private AiQueryDto toDto(AiQuery q, AiResponse r) {
        AiResponseDto respDto = null;
        if (r != null) {
            List<String> steps = List.of();
            if (r.getStepsJson() != null && !r.getStepsJson().isBlank()) {
                try {
                    steps = objectMapper.readValue(r.getStepsJson(), new TypeReference<List<String>>() {});
                } catch (Exception ignore) {}
            }

            List<AiSourceDto> sources = List.of();
            if (r.getSources() != null) {
                sources = r.getSources().stream()
                        .map(s -> new AiSourceDto(
                                s.getArticle().getId(),
                                s.getArticleTitle(),
                                s.getSimilarityScore(),
                                s.getVersionUsed()
                        ))
                        .toList();
            }

            respDto = new AiResponseDto(
                    r.getId(),
                    q.getId(),
                    r.getSummary(),
                    steps,
                    r.getRecommendations(),
                    r.getSourcesCount(),
                    r.getModelName(),
                    r.getResponseTimeMs(),
                    r.getCreatedAt(),
                    sources
            );
        }

        return new AiQueryDto(
                q.getId(),
                q.getTicket().getId(),
                q.getRequestedBy().getUsername(),
                q.getQueryText(),
                q.getStatus(),
                q.getErrorMessage(),
                q.getCreatedAt(),
                respDto
        );
    }
}

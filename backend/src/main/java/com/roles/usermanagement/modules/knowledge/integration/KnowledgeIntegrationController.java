package com.roles.usermanagement.modules.knowledge.integration;

import com.roles.usermanagement.modules.knowledge.entity.KnowledgeArticle;
import com.roles.usermanagement.modules.knowledge.repository.KnowledgeArticleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato de servicio para n8n (no es para el frontend ni para usuarios): validación relacional de
 * los artículos recuperados de Qdrant antes de construir el contexto, y listado de elegibles para
 * reconciliar el índice. Requiere X-Integration-Token (ROLE_INTEGRATION).
 */
@RestController
@RequestMapping("/api/integration/knowledge")
@Tag(name = "Integración n8n", description = "Endpoints de servicio autenticados con X-Integration-Token")
public class KnowledgeIntegrationController {

    public record ArticleRef(@NotNull Long articleId, @NotNull Integer contentVersion) {
    }

    public record EligibilityRequest(Integer schemaVersion,
                                     @NotEmpty @Size(max = 50) List<@Valid ArticleRef> articles) {
    }

    /** Contenido incluido solo cuando el artículo es elegible y la versión pedida es la vigente. */
    public record EligibilityItem(Long articleId, Integer requestedVersion, boolean usable, Integer currentVersion,
                                  String title, String problem, String symptoms, String cause, String solution) {
    }

    public record EligibilityResponse(int schemaVersion, List<EligibilityItem> articles) {
    }

    public record EligibleArticle(Long articleId, int contentVersion, String contentHash) {
    }

    private final KnowledgeArticleRepository articles;

    public KnowledgeIntegrationController(KnowledgeArticleRepository articles) {
        this.articles = articles;
    }

    @PostMapping("/eligibility")
    @Transactional(readOnly = true)
    @Operation(summary = "Validar existencia, elegibilidad y versión vigente de artículos recuperados",
            description = "usable=true solo si el artículo existe, está activo y verificado y la versión coincide. "
                    + "El contexto del prompt debe construirse con el contenido devuelto aquí, no con el del índice.")
    public EligibilityResponse eligibility(@Valid @RequestBody EligibilityRequest request) {
        List<EligibilityItem> result = new ArrayList<>();
        for (ArticleRef ref : request.articles()) {
            KnowledgeArticle article = articles.findById(ref.articleId()).orElse(null);
            boolean usable = article != null && article.isEligible() && article.getContentVersion() == ref.contentVersion();
            result.add(usable
                    ? new EligibilityItem(article.getId(), ref.contentVersion(), true, article.getContentVersion(),
                            article.getTitle(), article.getProblem(), article.getSymptoms(), article.getCause(), article.getSolution())
                    : new EligibilityItem(ref.articleId(), ref.contentVersion(), false,
                            article != null && article.isEligible() ? article.getContentVersion() : null,
                            null, null, null, null, null));
        }
        return new EligibilityResponse(1, result);
    }

    @GetMapping("/eligible")
    @Transactional(readOnly = true)
    @Operation(summary = "Artículos elegibles y sus versiones",
            description = "Para reconciliar: todo punto del índice cuyo artículo o versión no figure aquí debe retirarse.")
    public List<EligibleArticle> eligible() {
        return articles.findByActiveTrueAndVerifiedTrueOrderByIdAsc().stream()
                .map(article -> new EligibleArticle(article.getId(), article.getContentVersion(), article.getContentHash()))
                .toList();
    }
}

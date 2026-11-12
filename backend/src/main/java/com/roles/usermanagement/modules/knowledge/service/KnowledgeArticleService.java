package com.roles.usermanagement.modules.knowledge.service;

import com.roles.usermanagement.modules.knowledge.dto.ArticleRequest;
import com.roles.usermanagement.modules.knowledge.dto.ArticleResponse;
import com.roles.usermanagement.modules.knowledge.entity.KnowledgeArticle;
import com.roles.usermanagement.modules.knowledge.enums.IndexSyncStatus;
import com.roles.usermanagement.modules.knowledge.repository.KnowledgeArticleRepository;
import com.roles.usermanagement.modules.ticket.service.TicketAccessPolicy;
import com.roles.usermanagement.persistance.crud.UserCrudRepository;
import com.roles.usermanagement.persistance.entity.UserEntity;
import com.roles.usermanagement.web.dto.PageResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reglas editoriales de la base de conocimiento (RF02):
 * borrador → verificado (ADMIN) → activo (ADMIN). Un cambio de contenido técnico incrementa la
 * versión, revoca la verificación, desactiva el artículo y deja pendiente la sincronización del índice.
 */
@Service
public class KnowledgeArticleService {

    public enum Scope { PUBLISHED, MINE, ALL }

    private final KnowledgeArticleRepository articles;
    private final UserCrudRepository users;
    private final TicketAccessPolicy roles;

    public KnowledgeArticleService(KnowledgeArticleRepository articles, UserCrudRepository users, TicketAccessPolicy roles) {
        this.articles = articles;
        this.users = users;
        this.roles = roles;
    }

    @Transactional
    public ArticleResponse create(ArticleRequest request, Authentication auth) {
        requireAuthorRole(auth);
        UserEntity author = users.findById(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario no existe"));
        LocalDateTime now = LocalDateTime.now();
        KnowledgeArticle article = new KnowledgeArticle();
        applyContent(article, request);
        article.setAuthor(author);
        article.setVerified(false);
        article.setActive(false);
        article.setContentVersion(1);
        article.setIndexStatus(IndexSyncStatus.NOT_INDEXED);
        article.setCreatedAt(now);
        article.setUpdatedAt(now);
        return toResponse(articles.save(article));
    }

    /**
     * Edición de contenido. TECHNICIAN: solo sus borradores (no verificados). ADMIN: cualquiera.
     * Si el contenido cambia, el artículo vuelve a ser un borrador inactivo con una versión nueva.
     */
    @Transactional
    public ArticleResponse update(Long id, ArticleRequest request, Authentication auth) {
        KnowledgeArticle article = lock(id);
        if (!roles.isAdmin(auth)) {
            requireAuthorRole(auth);
            if (!article.getAuthor().getUsername().equals(auth.getName())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes editar tus propios borradores");
            }
            if (article.isVerified()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El artículo ya fue verificado; solo un ADMIN puede modificarlo");
            }
        }
        String previousHash = article.getContentHash();
        applyContent(article, request);
        if (!article.getContentHash().equals(previousHash)) {
            article.setContentVersion(article.getContentVersion() + 1);
            article.setVerified(false);
            article.setVerifiedBy(null);
            article.setVerifiedAt(null);
            article.setActive(false);
            invalidateIndex(article);
            article.setUpdatedAt(LocalDateTime.now());
        }
        return toResponse(article);
    }

    /** ADMIN confirma que revisó la solución de la versión vigente. */
    @Transactional
    public ArticleResponse verify(Long id, Authentication auth) {
        KnowledgeArticle article = lock(id);
        if (!article.isVerified()) {
            article.setVerified(true);
            article.setVerifiedBy(users.findById(auth.getName())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario no existe")));
            article.setVerifiedAt(LocalDateTime.now());
            article.setUpdatedAt(LocalDateTime.now());
        }
        return toResponse(article);
    }

    /** Solo un artículo verificado puede activarse; al activarse queda pendiente de indexación. */
    @Transactional
    public ArticleResponse activate(Long id) {
        KnowledgeArticle article = lock(id);
        if (!article.isVerified()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se pueden activar artículos verificados");
        }
        if (!article.isActive()) {
            article.setActive(true);
            boolean alreadyIndexed = article.getIndexStatus() == IndexSyncStatus.INDEXED
                    && Integer.valueOf(article.getContentVersion()).equals(article.getIndexedVersion());
            if (!alreadyIndexed) {
                article.setIndexStatus(IndexSyncStatus.PENDING);
                article.setIndexErrorCode(null);
            }
            article.setUpdatedAt(LocalDateTime.now());
        }
        return toResponse(article);
    }

    /** Deja de alimentar el asistente de inmediato; la limpieza del índice queda pendiente. */
    @Transactional
    public ArticleResponse deactivate(Long id) {
        KnowledgeArticle article = lock(id);
        if (article.isActive()) {
            article.setActive(false);
            invalidateIndex(article);
            article.setUpdatedAt(LocalDateTime.now());
        }
        return toResponse(article);
    }

    @Transactional(readOnly = true)
    public ArticleResponse getById(Long id, Authentication auth) {
        KnowledgeArticle article = articles.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El artículo no existe"));
        boolean allowed = article.isEligible() || roles.isAdmin(auth)
                || (roles.isTechnician(auth) && article.getAuthor().getUsername().equals(auth.getName()));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a este artículo");
        }
        return toResponse(article);
    }

    @Transactional(readOnly = true)
    public PageResponse<ArticleResponse> list(Scope scope, Integer page, Integer size, Authentication auth) {
        Pageable pageable = PageResponse.request(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        Page<KnowledgeArticle> result = switch (scope == null ? Scope.PUBLISHED : scope) {
            case PUBLISHED -> articles.findByActiveTrueAndVerifiedTrue(pageable);
            case MINE -> {
                requireAuthorRole(auth);
                yield articles.findByAuthorUsername(auth.getName(), pageable);
            }
            case ALL -> {
                if (!roles.isAdmin(auth)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un ADMIN puede consultar todos los artículos");
                }
                yield articles.findAll(pageable);
            }
        };
        return PageResponse.of(result, KnowledgeArticleService::toResponse);
    }

    private void requireAuthorRole(Authentication auth) {
        if (!roles.isAdmin(auth) && !roles.isTechnician(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo TECHNICIAN o ADMIN pueden redactar artículos");
        }
    }

    private KnowledgeArticle lock(Long id) {
        return articles.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El artículo no existe"));
    }

    /** Si hay vectores en el índice, deben retirarse; si nunca los hubo, no queda nada pendiente. */
    private static void invalidateIndex(KnowledgeArticle article) {
        boolean hasVectors = article.getIndexedVersion() != null || article.getIndexStatus() == IndexSyncStatus.ERROR;
        article.setIndexStatus(hasVectors ? IndexSyncStatus.PENDING : IndexSyncStatus.NOT_INDEXED);
        article.setIndexErrorCode(null);
    }

    private static void applyContent(KnowledgeArticle article, ArticleRequest request) {
        article.setTitle(request.title().trim());
        article.setProblem(request.problem().trim());
        article.setSymptoms(request.symptoms().trim());
        article.setCause(request.cause().trim());
        article.setSolution(request.solution().trim());
        article.setContentHash(hash(article));
    }

    static String hash(KnowledgeArticle article) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : new String[]{article.getTitle(), article.getProblem(), article.getSymptoms(),
                    article.getCause(), article.getSolution()}) {
                digest.update(part.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0x1F);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static ArticleResponse toResponse(KnowledgeArticle a) {
        return new ArticleResponse(a.getId(), a.getTitle(), a.getProblem(), a.getSymptoms(), a.getCause(), a.getSolution(),
                a.getAuthor().getUsername(), a.isVerified(),
                a.getVerifiedBy() == null ? null : a.getVerifiedBy().getUsername(), a.getVerifiedAt(),
                a.isActive(), a.isEligible(), a.getContentVersion(), a.getIndexStatus(), a.getIndexedVersion(),
                a.getIndexedAt(), a.getIndexErrorCode(), a.getCreatedAt(), a.getUpdatedAt());
    }
}

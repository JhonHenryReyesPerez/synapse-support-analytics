package com.roles.usermanagement.modules.knowledge.controller;

import com.roles.usermanagement.modules.knowledge.dto.ArticleRequest;
import com.roles.usermanagement.modules.knowledge.dto.ArticleResponse;
import com.roles.usermanagement.modules.knowledge.service.KnowledgeArticleService;
import com.roles.usermanagement.modules.knowledge.service.KnowledgeArticleService.Scope;
import com.roles.usermanagement.web.config.OpenApiConfig;
import com.roles.usermanagement.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge/articles")
@Tag(name = "Base de conocimiento", description = "Artículos técnicos verificados que respaldan al asistente (RF02)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class KnowledgeArticleController {

    private final KnowledgeArticleService service;

    public KnowledgeArticleController(KnowledgeArticleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar artículos",
            description = "scope=PUBLISHED (por defecto, cualquier rol): activos y verificados. "
                    + "scope=MINE (TECHNICIAN/ADMIN): los redactados por el usuario. scope=ALL: solo ADMIN.")
    public PageResponse<ArticleResponse> list(@RequestParam(required = false) Scope scope,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size,
                                              Authentication auth) {
        return service.list(scope, page, size, auth);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar un artículo",
            description = "Publicado: cualquier rol. No publicado: su autor técnico o un ADMIN.")
    public ArticleResponse getById(@PathVariable Long id, Authentication auth) {
        return service.getById(id, auth);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Crear borrador", description = "Nace no verificado e inactivo, con versión 1.")
    public ResponseEntity<ArticleResponse> create(@Valid @RequestBody ArticleRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, auth));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Editar contenido",
            description = "TECHNICIAN: solo sus borradores. ADMIN: cualquiera. Un cambio de contenido incrementa la "
                    + "versión, revoca la verificación y desactiva el artículo.")
    public ArticleResponse update(@PathVariable Long id, @Valid @RequestBody ArticleRequest request, Authentication auth) {
        return service.update(id, request, auth);
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Verificar la versión vigente (ADMIN)", description = "Registra quién verificó y cuándo.")
    public ArticleResponse verify(@PathVariable Long id, Authentication auth) {
        return service.verify(id, auth);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activar un artículo verificado (ADMIN)", description = "Queda pendiente de indexación.")
    public ArticleResponse activate(@PathVariable Long id) {
        return service.activate(id);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar (ADMIN)",
            description = "Deja de ser fuente de nuevas respuestas de inmediato; no se elimina ni altera respuestas históricas.")
    public ArticleResponse deactivate(@PathVariable Long id) {
        return service.deactivate(id);
    }
}

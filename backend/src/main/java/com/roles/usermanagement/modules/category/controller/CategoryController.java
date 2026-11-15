package com.roles.usermanagement.modules.category.controller;

import com.roles.usermanagement.modules.category.dto.CategoryRequest;
import com.roles.usermanagement.modules.category.dto.CategoryResponse;
import com.roles.usermanagement.modules.category.service.CategoryService;
import com.roles.usermanagement.modules.ticket.service.TicketAccessPolicy;
import com.roles.usermanagement.web.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categorías", description = "Catálogo de categorías de incidentes")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class CategoryController {

    private final CategoryService categoryService;
    private final TicketAccessPolicy policy;

    public CategoryController(CategoryService categoryService, TicketAccessPolicy policy) {
        this.categoryService = categoryService;
        this.policy = policy;
    }

    @GetMapping
    @Operation(summary = "Listar categorías",
            description = "Devuelve las categorías activas. includeInactive=true solo tiene efecto para ADMIN.")
    public List<CategoryResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive,
                                       Authentication auth) {
        return categoryService.list(includeInactive && policy.isAdmin(auth));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear categoría (ADMIN)")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Editar nombre y descripción (ADMIN)")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activar categoría (ADMIN)")
    public CategoryResponse activate(@PathVariable Long id) {
        return categoryService.setActive(id, true);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar categoría (ADMIN)",
            description = "No se elimina: los tickets existentes la conservan, pero no admite tickets nuevos.")
    public CategoryResponse deactivate(@PathVariable Long id) {
        return categoryService.setActive(id, false);
    }
}

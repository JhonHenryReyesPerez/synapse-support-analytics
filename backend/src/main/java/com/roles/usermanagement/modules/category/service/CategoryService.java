package com.roles.usermanagement.modules.category.service;

import com.roles.usermanagement.modules.category.dto.CategoryRequest;
import com.roles.usermanagement.modules.category.dto.CategoryResponse;
import com.roles.usermanagement.modules.category.entity.Category;
import com.roles.usermanagement.modules.category.repository.CategoryRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CategoryService {

    /**
     * Valores del enum anterior (y sus alias en inglés) aceptados como entrada por compatibilidad.
     * Solo traducen la entrada: en base de datos existe una única categoría por valor.
     */
    private static final Map<String, String> LEGACY_ALIASES = Map.of(
            "REDES", "Conectividad",
            "NETWORK", "Conectividad",
            "ACCESOS", "Accesos",
            "ACCESS", "Accesos",
            "OTROS", "Otros",
            "OTHER", "Otros");

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(boolean includeInactive) {
        List<Category> result = includeInactive
                ? categories.findAllByOrderByNameAsc()
                : categories.findByActiveTrueOrderByNameAsc();
        return result.stream().map(CategoryService::toResponse).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una categoría con ese nombre");
        }
        Category category = Category.builder()
                .name(name)
                .description(clean(request.description()))
                .active(true)
                .build();
        return toResponse(categories.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = find(id);
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una categoría con ese nombre");
        }
        category.setName(name);
        category.setDescription(clean(request.description()));
        return toResponse(category);
    }

    /** Activar o desactivar; las categorías no se eliminan porque los tickets históricos las referencian. */
    @Transactional
    public CategoryResponse setActive(Long id, boolean active) {
        Category category = find(id);
        category.setActive(active);
        return toResponse(category);
    }

    /**
     * Categoría para crear o editar un ticket: debe existir y estar activa.
     * Se identifica por id o, por compatibilidad, por nombre o alias del enum anterior.
     */
    @Transactional(readOnly = true)
    public Category requireActive(Long categoryId, String nameOrAlias) {
        Category category = resolve(categoryId, nameOrAlias)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría indicada no existe"));
        if (!category.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría indicada no está activa");
        }
        return category;
    }

    /** Categoría para filtrar (activa o no). Vacío si no se indicó ninguna; 400 si no existe. */
    @Transactional(readOnly = true)
    public Optional<Category> resolveFilter(Long categoryId, String nameOrAlias) {
        if (categoryId == null && (nameOrAlias == null || nameOrAlias.isBlank())) {
            return Optional.empty();
        }
        return Optional.of(resolve(categoryId, nameOrAlias)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría indicada no existe")));
    }

    private Optional<Category> resolve(Long categoryId, String nameOrAlias) {
        if (categoryId != null) {
            return categories.findById(categoryId);
        }
        if (nameOrAlias == null || nameOrAlias.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría es obligatoria");
        }
        String value = nameOrAlias.trim();
        String canonical = LEGACY_ALIASES.getOrDefault(value.toUpperCase(Locale.ROOT), value);
        return categories.findByNameIgnoreCase(canonical);
    }

    private Category find(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La categoría no existe"));
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(), category.isActive());
    }
}

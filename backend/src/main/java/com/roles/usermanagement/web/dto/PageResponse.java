package com.roles.usermanagement.web.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Página de resultados con contrato estable (no expone la estructura interna de Spring Data). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Valida page >= 0 y 1 <= size <= 100 (20 por defecto) y aplica el orden estable indicado. */
    public static Pageable request(Integer page, Integer size, Sort sort) {
        int number = page == null ? 0 : page;
        int length = size == null ? DEFAULT_SIZE : size;
        if (number < 0 || length < 1 || length > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page debe ser 0 o mayor y size debe estar entre 1 y " + MAX_SIZE);
        }
        return PageRequest.of(number, length, sort);
    }
}

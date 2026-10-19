package co.edu.javeriana.bpmn.common.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Página de resultados que sale por la API. Nunca se expone el Page de Spring Data:
 * su JSON cambia entre versiones y arrastra el objeto pageable.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> de(Page<E> pagina, Function<E, T> mapeo) {
        return new PageResponse<>(
                pagina.getContent().stream().map(mapeo).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }
}

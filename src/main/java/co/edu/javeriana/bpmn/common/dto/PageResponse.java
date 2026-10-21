package co.edu.javeriana.bpmn.common.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Página de resultados que sale por la API. Nunca se expone el Page de Spring Data:
 * su JSON cambia entre versiones y arrastra el objeto pageable.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    /**
     * Convierte una página de Spring Data (de entidades) en una PageResponse.
     * Cada elemento se transforma con la función mapeo, normalmente de entidad a DTO, así la entidad no sale por la API.
     * @param pagina página obtenida del repositorio
     * @param mapeo función que convierte cada entidad en su representación de salida
     */
    public static <E, T> PageResponse<T> de(Page<E> pagina, Function<E, T> mapeo) {
        // Se copian solo los datos de paginación que el cliente necesita: contenido, página (desde 0), tamaño, total de registros y total de páginas.
        return new PageResponse<>(
                pagina.getContent().stream().map(mapeo).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }
}

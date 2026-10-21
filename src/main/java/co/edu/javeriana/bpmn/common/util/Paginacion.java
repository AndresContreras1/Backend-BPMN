package co.edu.javeriana.bpmn.common.util;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import co.edu.javeriana.bpmn.common.exception.ParametroInvalidoException;

/**
 * Lista blanca de campos de ordenamiento: el cliente no puede ordenar por cualquier atributo
 * de la entidad (por ejemplo, por la contraseña o por el empresaId). Además desempata por el id,
 * para que un registro no se repita ni se pierda entre páginas cuando dos tienen el mismo valor.
 */
public final class Paginacion {

    // Campo único que se agrega al final del orden para que sea estable.
    private static final String DESEMPATE = "id";

    // Clase de utilidades: no se instancia.
    private Paginacion() {
    }

    /**
     * Valida el orden pedido por el cliente y devuelve el Pageable que debe usarse en la consulta.
     * @param pageable página, tamaño y orden recibidos en la petición
     * @param camposPermitidos únicos atributos por los que se permite ordenar
     * @throws ParametroInvalidoException si se pide ordenar por un campo fuera de la lista
     */
    public static Pageable validarOrden(Pageable pageable, Set<String> camposPermitidos) {
        // 1. Rechaza cualquier campo de orden que no esté en la lista blanca.
        for (Sort.Order orden : pageable.getSort()) {
            if (!camposPermitidos.contains(orden.getProperty())) {
                throw new ParametroInvalidoException("No se puede ordenar por '" + orden.getProperty()
                        + "'. Campos permitidos: " + String.join(", ", camposPermitidos.stream().sorted().toList()));
            }
        }
        // 2. Si el cliente ya ordena por id, no hace falta agregar el desempate.
        if (pageable.getSort().getOrderFor(DESEMPATE) != null) {
            return pageable;
        }
        // 3. Mismo número y tamaño de página, con el id como último criterio de orden.
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                pageable.getSort().and(Sort.by(DESEMPATE)));
    }
}

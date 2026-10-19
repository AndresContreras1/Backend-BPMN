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

    private static final String DESEMPATE = "id";

    private Paginacion() {
    }

    public static Pageable validarOrden(Pageable pageable, Set<String> camposPermitidos) {
        for (Sort.Order orden : pageable.getSort()) {
            if (!camposPermitidos.contains(orden.getProperty())) {
                throw new ParametroInvalidoException("No se puede ordenar por '" + orden.getProperty()
                        + "'. Campos permitidos: " + String.join(", ", camposPermitidos.stream().sorted().toList()));
            }
        }
        if (pageable.getSort().getOrderFor(DESEMPATE) != null) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                pageable.getSort().and(Sort.by(DESEMPATE)));
    }
}

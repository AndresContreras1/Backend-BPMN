package co.edu.javeriana.bpmn.common.util;

import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import co.edu.javeriana.bpmn.common.exception.ParametroInvalidoException;

/**
 * Lista blanca de campos de ordenamiento: el cliente no puede ordenar por cualquier atributo
 * de la entidad (por ejemplo, por la contraseña o por el empresaId).
 */
public final class Paginacion {

    private Paginacion() {
    }

    public static Pageable validarOrden(Pageable pageable, Set<String> camposPermitidos) {
        for (Sort.Order orden : pageable.getSort()) {
            if (!camposPermitidos.contains(orden.getProperty())) {
                throw new ParametroInvalidoException("No se puede ordenar por '" + orden.getProperty()
                        + "'. Campos permitidos: " + String.join(", ", camposPermitidos.stream().sorted().toList()));
            }
        }
        return pageable;
    }
}

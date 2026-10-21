package co.edu.javeriana.bpmn.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import co.edu.javeriana.bpmn.common.exception.ParametroInvalidoException;
import co.edu.javeriana.bpmn.common.util.Paginacion;

/**
 * Pruebas unitarias de Paginacion.validarOrden: solo permite ordenar por campos de una lista blanca
 * y siempre agrega el id como desempate para que el orden sea estable.
 */
class PaginacionTest {

    // Campos por los que se acepta ordenar.
    private static final Set<String> PERMITIDOS = Set.of("nombre", "id");

    // Escenario: pedir orden por nombre descendente.
    // Esperado: se conserva página, tamaño y dirección, y se añade id después de nombre.
    @Test
    void Paginacion_validarOrden_AgregaElIdComoDesempate() {
        // Preparar: página 2, tamaño 10, orden por nombre descendente.
        Pageable pedido = PageRequest.of(2, 10, Sort.by("nombre").descending());

        // Actuar: validar el orden pedido.
        Pageable orden = Paginacion.validarOrden(pedido, PERMITIDOS);

        // Comprobar: paginación intacta y orden nombre, id.
        assertThat(orden.getPageNumber()).isEqualTo(2);
        assertThat(orden.getPageSize()).isEqualTo(10);
        assertThat(orden.getSort().stream().map(Sort.Order::getProperty)).containsExactly("nombre", "id");
        assertThat(orden.getSort().getOrderFor("nombre").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    // Escenario: el orden pedido ya es por id.
    // Esperado: se devuelve el mismo objeto sin cambios (isSameAs compara la referencia).
    @Test
    void Paginacion_validarOrden_NoDuplicaElIdSiYaVieneEnElOrden() {
        Pageable pedido = PageRequest.of(0, 5, Sort.by("id").descending());

        assertThat(Paginacion.validarOrden(pedido, PERMITIDOS)).isSameAs(pedido);
    }

    // Escenario: no se pide ningún orden.
    // Esperado: el orden queda solo por id.
    @Test
    void Paginacion_validarOrden_SinOrdenDejaSoloElId() {
        Pageable orden = Paginacion.validarOrden(PageRequest.of(0, 5), PERMITIDOS);

        assertThat(orden.getSort().stream().map(Sort.Order::getProperty)).containsExactly("id");
    }

    // Escenario: ordenar por un campo no permitido (password).
    // Esperado: ParametroInvalidoException cuyo mensaje menciona el campo.
    @Test
    void Paginacion_validarOrden_RechazaCampoNoPermitido() {
        Pageable pedido = PageRequest.of(0, 5, Sort.by("password"));

        // assertThatThrownBy ejecuta el código y verifica el tipo de excepción y su mensaje.
        assertThatThrownBy(() -> Paginacion.validarOrden(pedido, PERMITIDOS))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("password");
    }
}

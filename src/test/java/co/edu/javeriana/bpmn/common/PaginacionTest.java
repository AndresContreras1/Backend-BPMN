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

class PaginacionTest {

    private static final Set<String> PERMITIDOS = Set.of("nombre", "id");

    @Test
    void Paginacion_validarOrden_AgregaElIdComoDesempate() {
        Pageable pedido = PageRequest.of(2, 10, Sort.by("nombre").descending());

        Pageable orden = Paginacion.validarOrden(pedido, PERMITIDOS);

        assertThat(orden.getPageNumber()).isEqualTo(2);
        assertThat(orden.getPageSize()).isEqualTo(10);
        assertThat(orden.getSort().stream().map(Sort.Order::getProperty)).containsExactly("nombre", "id");
        assertThat(orden.getSort().getOrderFor("nombre").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void Paginacion_validarOrden_NoDuplicaElIdSiYaVieneEnElOrden() {
        Pageable pedido = PageRequest.of(0, 5, Sort.by("id").descending());

        assertThat(Paginacion.validarOrden(pedido, PERMITIDOS)).isSameAs(pedido);
    }

    @Test
    void Paginacion_validarOrden_SinOrdenDejaSoloElId() {
        Pageable orden = Paginacion.validarOrden(PageRequest.of(0, 5), PERMITIDOS);

        assertThat(orden.getSort().stream().map(Sort.Order::getProperty)).containsExactly("id");
    }

    @Test
    void Paginacion_validarOrden_RechazaCampoNoPermitido() {
        Pageable pedido = PageRequest.of(0, 5, Sort.by("password"));

        assertThatThrownBy(() -> Paginacion.validarOrden(pedido, PERMITIDOS))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("password");
    }
}

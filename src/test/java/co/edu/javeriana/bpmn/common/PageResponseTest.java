package co.edu.javeriana.bpmn.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import co.edu.javeriana.bpmn.common.dto.PageResponse;

class PageResponseTest {

    @Test
    void PageResponse_de_CopiaTotalesYMapeaContenido() {
        PageImpl<Integer> pagina = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(1, 3), 10);

        PageResponse<String> respuesta = PageResponse.de(pagina, n -> "n" + n);

        assertThat(respuesta.content()).containsExactly("n1", "n2", "n3");
        assertThat(respuesta.page()).isEqualTo(1);
        assertThat(respuesta.size()).isEqualTo(3);
        assertThat(respuesta.totalElements()).isEqualTo(10);
        assertThat(respuesta.totalPages()).isEqualTo(4);
    }

    @Test
    void PageResponse_paginaVacia_SinElementos() {
        PageImpl<Integer> pagina = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

        PageResponse<Integer> respuesta = PageResponse.de(pagina, n -> n);

        assertThat(respuesta.content()).isEmpty();
        assertThat(respuesta.totalPages()).isZero();
    }
}

package co.edu.javeriana.bpmn.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import co.edu.javeriana.bpmn.common.dto.PageResponse;

/**
 * Pruebas unitarias de PageResponse: el objeto de respuesta que convierte una página de Spring Data
 * en el formato que devuelve la API. No usa Spring ni base de datos.
 */
class PageResponseTest {

    // Escenario: convertir una página con tres números, mapeando cada uno a texto.
    // Esperado: el contenido se transforma y se copian número de página, tamaño y totales.
    @Test
    void PageResponse_de_CopiaTotalesYMapeaContenido() {
        // Preparar: página índice 1, tamaño 3, con 10 elementos en total (de ahí salen 4 páginas).
        PageImpl<Integer> pagina = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(1, 3), 10);

        // Actuar: la función n -> "n" + n transforma cada elemento.
        PageResponse<String> respuesta = PageResponse.de(pagina, n -> "n" + n);

        // Comprobar: contenido transformado y metadatos copiados.
        assertThat(respuesta.content()).containsExactly("n1", "n2", "n3");
        assertThat(respuesta.page()).isEqualTo(1);
        assertThat(respuesta.size()).isEqualTo(3);
        assertThat(respuesta.totalElements()).isEqualTo(10);
        assertThat(respuesta.totalPages()).isEqualTo(4);
    }

    // Escenario: página sin elementos.
    // Esperado: contenido vacío y cero páginas.
    @Test
    void PageResponse_paginaVacia_SinElementos() {
        PageImpl<Integer> pagina = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

        PageResponse<Integer> respuesta = PageResponse.de(pagina, n -> n);

        assertThat(respuesta.content()).isEmpty();
        assertThat(respuesta.totalPages()).isZero();
    }
}

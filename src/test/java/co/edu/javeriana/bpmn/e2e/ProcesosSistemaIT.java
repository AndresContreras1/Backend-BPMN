package co.edu.javeriana.bpmn.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.javeriana.bpmn.e2e.paginas.LoginPagina;
import co.edu.javeriana.bpmn.e2e.paginas.ProcesosPagina;

/**
 * Casos medios: una acción en la lista y comprobar cómo cambia. Los números se calculan con la lista
 * inicial, no se queman: así la prueba no depende de cuántos procesos traiga el inicializador.
 */
class ProcesosSistemaIT extends SistemaBase {

    private ProcesosPagina procesos;

    @BeforeEach
    void entrar() {
        new LoginPagina(driver, wait).abrir().entrarComo("admin@demo.com", "admin123");
        procesos = new ProcesosPagina(driver, wait).abrir();
    }

    @Test
    void HU06_eliminarUnProceso_LaListaDisminuyeEnUnoYQuedaEnLosEliminados() {
        int antes = procesos.cantidad();

        procesos.eliminar("Aprobación de compras");

        procesos.esperarCantidad(antes - 1);
        assertThat(procesos.nombres()).doesNotContain("Aprobación de compras");

        procesos.verEliminados();
        procesos.esperarCantidad(2);
        assertThat(procesos.nombres()).contains("Aprobación de compras", "Control de calidad 2024");
    }

    @Test
    void HU07_filtrarPorEstado_SoloQuedanLosPublicados() {
        procesos.filtrarPorEstado("PUBLICADO");

        procesos.esperarCantidad(1);
        assertThat(procesos.nombres()).containsExactly("Solicitud de vacaciones");
        assertThat(procesos.estados()).containsOnly("Publicado");
    }
}

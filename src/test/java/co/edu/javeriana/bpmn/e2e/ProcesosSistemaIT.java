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
// Suite de sistema de la lista de procesos: eliminar y filtrar, con el admin de la empresa demo.
class ProcesosSistemaIT extends SistemaBase {

    // Page Object de la lista, compartido por las pruebas de esta clase.
    private ProcesosPagina procesos;

    // Antes de cada prueba: iniciar sesión como admin y abrir la lista en vista de tabla.
    @BeforeEach
    void entrar() {
        new LoginPagina(driver, wait).abrir().entrarComo("admin@demo.com", "admin123");
        procesos = new ProcesosPagina(driver, wait).abrir();
    }

    // Escenario: eliminar un proceso desde la lista.
    // Esperado: la lista baja en uno y el proceso aparece al ver los eliminados.
    @Test
    void HU06_eliminarUnProceso_LaListaDisminuyeEnUnoYQuedaEnLosEliminados() {
        // Preparar: se guarda la cantidad inicial para no depender de un número fijo.
        int antes = procesos.cantidad();

        // Actuar: eliminar el proceso por nombre y esperar que la lista tenga una fila menos.
        procesos.eliminar("Aprobación de compras");

        procesos.esperarCantidad(antes - 1);
        // Comprobar: ya no está en la lista activa.
        assertThat(procesos.nombres()).doesNotContain("Aprobación de compras");

        // Comprobar: en la vista de eliminados hay 2 (el recién borrado y uno que ya estaba en los datos demo).
        procesos.verEliminados();
        procesos.esperarCantidad(2);
        assertThat(procesos.nombres()).contains("Aprobación de compras", "Control de calidad 2024");
    }

    // Escenario: filtrar la lista por el estado PUBLICADO.
    // Esperado: queda solo un proceso, con estado Publicado.
    @Test
    void HU07_filtrarPorEstado_SoloQuedanLosPublicados() {
        // Actuar: elegir el estado en el filtro y esperar a que la lista se actualice a 1 fila.
        procesos.filtrarPorEstado("PUBLICADO");

        procesos.esperarCantidad(1);
        // Comprobar: exactamente ese proceso y todas las insignias en Publicado.
        assertThat(procesos.nombres()).containsExactly("Solicitud de vacaciones");
        assertThat(procesos.estados()).containsOnly("Publicado");
    }
}

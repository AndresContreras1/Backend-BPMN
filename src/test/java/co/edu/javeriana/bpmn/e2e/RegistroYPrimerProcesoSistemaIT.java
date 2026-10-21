package co.edu.javeriana.bpmn.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import co.edu.javeriana.bpmn.e2e.paginas.InicioPagina;
import co.edu.javeriana.bpmn.e2e.paginas.LoginPagina;
import co.edu.javeriana.bpmn.e2e.paginas.MenuPrincipal;
import co.edu.javeriana.bpmn.e2e.paginas.ProcesoDetallePagina;
import co.edu.javeriana.bpmn.e2e.paginas.ProcesosPagina;
import co.edu.javeriana.bpmn.e2e.paginas.RegistroPagina;

/**
 * Caso de uso completo 1 (HU-01, HU-03, HU-04, HU-07): una empresa nueva se registra, su administrador
 * crea el primer proceso y lo encuentra en el inicio y en la lista, también después de volver a entrar.
 */
class RegistroYPrimerProcesoSistemaIT extends SistemaBase {

    private static final String CORREO = "valeria@reclamos.com";
    private static final String CLAVE = "ClaveSegura2026";

    @Test
    void HU01_HU04_registrarEmpresaYCrearSuPrimerProceso_ApareceEnElInicioYEnLaLista() {
        InicioPagina inicio = new RegistroPagina(driver, wait).abrir()
                .registrar("Reclamos Ágiles S.A.S.", "901555777-2", "Valeria Ríos", CORREO, CLAVE);

        assertThat(inicio.saludo()).isEqualTo("Hola, Valeria");
        assertThat(inicio.valorDe("total")).isZero();
        assertThat(new MenuPrincipal(driver, wait).rol()).isEqualTo("ADMINISTRADOR");

        ProcesoDetallePagina detalle = inicio.nuevoProceso()
                .crear("Atención de reclamos", "Servicio al cliente", "Recepción, análisis y respuesta de reclamos");
        assertThat(detalle.titulo()).contains("Atención de reclamos");
        assertThat(detalle.estado()).isEqualTo("Borrador");

        inicio.abrir();
        assertThat(inicio.valorDe("total")).isEqualTo(1);
        assertThat(inicio.valorDe("borradores")).isEqualTo(1);
        assertThat(new ProcesosPagina(driver, wait).abrir().nombres()).containsExactly("Atención de reclamos");

        // El proceso es de la empresa: sigue ahí al cerrar la sesión y volver a entrar
        LoginPagina login = new MenuPrincipal(driver, wait).cerrarSesion();
        assertThat(login.entrarComo(CORREO, CLAVE).valorDe("total")).isEqualTo(1);
    }
}

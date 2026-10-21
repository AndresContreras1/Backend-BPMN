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
// Suite de sistema del flujo de una empresa nueva: registro, primer proceso y reingreso.
class RegistroYPrimerProcesoSistemaIT extends SistemaBase {

    // Credenciales de la empresa nueva, reutilizadas para volver a entrar.
    private static final String CORREO = "valeria@reclamos.com";
    private static final String CLAVE = "ClaveSegura2026";

    // Escenario: registrar una empresa, crear su primer proceso y volver a entrar.
    // Esperado: aparece en el inicio y en la lista, y persiste tras cerrar sesión.
    @Test
    void HU01_HU04_registrarEmpresaYCrearSuPrimerProceso_ApareceEnElInicioYEnLaLista() {
        // Actuar: registrar la empresa; el registro deja al usuario dentro, en el inicio.
        InicioPagina inicio = new RegistroPagina(driver, wait).abrir()
                .registrar("Reclamos Ágiles S.A.S.", "901555777-2", "Valeria Ríos", CORREO, CLAVE);

        // Comprobar: saludo con el primer nombre, cero procesos y rol de administrador.
        assertThat(inicio.saludo()).isEqualTo("Hola, Valeria");
        assertThat(inicio.valorDe("total")).isZero();
        assertThat(new MenuPrincipal(driver, wait).rol()).isEqualTo("ADMINISTRADOR");

        // Actuar: crear el primer proceso desde el inicio.
        ProcesoDetallePagina detalle = inicio.nuevoProceso()
                .crear("Atención de reclamos", "Servicio al cliente", "Recepción, análisis y respuesta de reclamos");
        // Comprobar: la ficha muestra el nombre y el estado inicial Borrador.
        assertThat(detalle.titulo()).contains("Atención de reclamos");
        assertThat(detalle.estado()).isEqualTo("Borrador");

        // Comprobar: al volver al inicio los indicadores muestran 1 proceso y 1 borrador.
        inicio.abrir();
        assertThat(inicio.valorDe("total")).isEqualTo(1);
        assertThat(inicio.valorDe("borradores")).isEqualTo(1);
        // Comprobar: la lista contiene solo ese proceso.
        assertThat(new ProcesosPagina(driver, wait).abrir().nombres()).containsExactly("Atención de reclamos");

        // El proceso es de la empresa: sigue ahí al cerrar la sesión y volver a entrar
        // Cerrar sesión y entrar de nuevo: el dato sigue ahí.
        LoginPagina login = new MenuPrincipal(driver, wait).cerrarSesion();
        assertThat(login.entrarComo(CORREO, CLAVE).valorDe("total")).isEqualTo(1);
    }
}

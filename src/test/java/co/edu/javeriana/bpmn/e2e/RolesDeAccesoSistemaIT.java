package co.edu.javeriana.bpmn.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.support.ui.ExpectedConditions;

import co.edu.javeriana.bpmn.e2e.paginas.InicioPagina;
import co.edu.javeriana.bpmn.e2e.paginas.LoginPagina;
import co.edu.javeriana.bpmn.e2e.paginas.MenuPrincipal;
import co.edu.javeriana.bpmn.e2e.paginas.ProcesosPagina;
import co.edu.javeriana.bpmn.e2e.paginas.UsuariosPagina;

/**
 * Caso de uso completo 3 (HU-02 y HU-03): el administrador invita a un lector; el lector entra con la
 * contraseña inicial, ve los procesos de la empresa y no tiene cómo crearlos, ni escribiendo la URL.
 */
class RolesDeAccesoSistemaIT extends SistemaBase {

    @Test
    void HU02_invitarUnLector_ConsultaLosProcesosPeroNoPuedeCrearlos() {
        new LoginPagina(driver, wait).abrir().entrarComo("admin@demo.com", "admin123");
        UsuariosPagina usuarios = new UsuariosPagina(driver, wait).abrir()
                .invitar("Pedro Consulta", "pedro@demo.com", "LECTOR", "ClaveInicial2026");
        assertThat(usuarios.correos()).contains("pedro@demo.com");

        LoginPagina login = new MenuPrincipal(driver, wait).cerrarSesion();
        InicioPagina inicio = login.entrarComo("pedro@demo.com", "ClaveInicial2026");

        assertThat(new MenuPrincipal(driver, wait).rol()).isEqualTo("LECTOR");
        assertThat(inicio.puedeCrearProcesos()).isFalse();

        ProcesosPagina procesos = new ProcesosPagina(driver, wait).abrir();
        assertThat(procesos.nombres()).contains("Solicitud de vacaciones", "Aprobación de compras");
        assertThat(procesos.puedeCrear()).isFalse();

        // El guard de rol devuelve al inicio; y si alguien lo salta, la API responde 403
        abrir("/procesos/nuevo");
        wait.until(ExpectedConditions.urlContains("/inicio"));
    }
}

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
// Suite de sistema de roles: lo que un usuario LECTOR puede y no puede hacer.
class RolesDeAccesoSistemaIT extends SistemaBase {

    // Escenario: el admin invita a un lector y este entra.
    // Esperado: ve los procesos pero no puede crearlos, ni siquiera escribiendo la URL.
    @Test
    void HU02_invitarUnLector_ConsultaLosProcesosPeroNoPuedeCrearlos() {
        // Preparar: el admin inicia sesión.
        new LoginPagina(driver, wait).abrir().entrarComo("admin@demo.com", "admin123");
        // Actuar: invitar al nuevo usuario con rol LECTOR.
        UsuariosPagina usuarios = new UsuariosPagina(driver, wait).abrir()
                .invitar("Pedro Consulta", "pedro@demo.com", "LECTOR", "ClaveInicial2026");
        // Comprobar: el correo aparece en la lista de usuarios.
        assertThat(usuarios.correos()).contains("pedro@demo.com");

        // Cambio de usuario: cerrar la sesión del admin y entrar como el lector con su clave inicial.
        LoginPagina login = new MenuPrincipal(driver, wait).cerrarSesion();
        InicioPagina inicio = login.entrarComo("pedro@demo.com", "ClaveInicial2026");

        // Comprobar: el rol es LECTOR y no existe el botón de crear en el inicio.
        assertThat(new MenuPrincipal(driver, wait).rol()).isEqualTo("LECTOR");
        assertThat(inicio.puedeCrearProcesos()).isFalse();

        // Comprobar: ve los procesos de la empresa pero sin botón de crear.
        ProcesosPagina procesos = new ProcesosPagina(driver, wait).abrir();
        assertThat(procesos.nombres()).contains("Solicitud de vacaciones", "Aprobación de compras");
        assertThat(procesos.puedeCrear()).isFalse();

        // Actuar: intentar entrar a la pantalla de creación escribiendo la URL.
        // El guard de rol devuelve al inicio; y si alguien lo salta, la API responde 403
        abrir("/procesos/nuevo");
        // Comprobar: el guard (protección de ruta) lo devuelve al inicio.
        wait.until(ExpectedConditions.urlContains("/inicio"));
    }
}

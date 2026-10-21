package co.edu.javeriana.bpmn.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import co.edu.javeriana.bpmn.e2e.paginas.CompartidosPagina;
import co.edu.javeriana.bpmn.e2e.paginas.InicioPagina;
import co.edu.javeriana.bpmn.e2e.paginas.LoginPagina;
import co.edu.javeriana.bpmn.e2e.paginas.MenuPrincipal;
import co.edu.javeriana.bpmn.e2e.paginas.ProcesoDetallePagina;
import co.edu.javeriana.bpmn.e2e.paginas.ProcesosPagina;

/**
 * Caso de uso completo 2 (HU-23 y aislamiento entre empresas): Facimus comparte un proceso por NIT con
 * Comercial Andina; Andina lo consulta en solo lectura y el proceso no aparece entre los suyos.
 */
// Suite de sistema (Selenium sobre el frontend real). Hereda de SistemaBase: navegador, base demo limpia y servidor arriba.
class CompartirEntreEmpresasSistemaIT extends SistemaBase {

    // NIT de la empresa invitada (Comercial Andina), dato de la base demo.
    private static final String NIT_ANDINA = "800987654-3";

    // Escenario: la empresa dueña comparte un proceso con otra por NIT.
    // Esperado: la invitada lo ve en solo lectura, en su lista de compartidos, y no entre sus propios procesos.
    @Test
    void HU23_compartirConOtraEmpresa_LaInvitadaLoConsultaEnSoloLecturaSinQueSeaSuyo() {
        // Preparar: el admin de la empresa dueña inicia sesión.
        new LoginPagina(driver, wait).abrir().entrarComo("admin@demo.com", "admin123");
        // Actuar: abre el proceso desde la lista y lo comparte con el NIT de Andina.
        new ProcesosPagina(driver, wait).abrir()
                .abrirProceso("Aprobación de compras")
                .compartirCon(NIT_ANDINA);

        // Cambio de usuario: se cierra la sesión y entra el admin de la empresa invitada.
        LoginPagina login = new MenuPrincipal(driver, wait).cerrarSesion();
        InicioPagina inicioAndina = login.entrarComo("admin@andina.com", "admin123");
        // Los datos demo ya compartían "Solicitud de vacaciones": ahora son dos
        // Comprobar: el indicador de compartidos sube a 2 (uno que ya existía más el nuevo).
        assertThat(inicioAndina.valorDe("compartidos")).isEqualTo(2);

        // Comprobar: la página de compartidos lista ambos procesos.
        CompartidosPagina compartidos = new CompartidosPagina(driver, wait).abrir();
        assertThat(compartidos.nombres()).contains("Aprobación de compras", "Solicitud de vacaciones");

        // Comprobar: al abrirlo se ve como de otra empresa y sin opción de edición.
        ProcesoDetallePagina detalle = compartidos.abrirProceso("Aprobación de compras");
        assertThat(detalle.compartidoPorOtraEmpresa()).isTrue();
        assertThat(detalle.editable()).isFalse();

        // Comprobar: no aparece entre los procesos propios de la empresa invitada.
        assertThat(new ProcesosPagina(driver, wait).abrir().nombres()).doesNotContain("Aprobación de compras");
    }
}

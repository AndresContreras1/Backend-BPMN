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
class CompartirEntreEmpresasSistemaIT extends SistemaBase {

    private static final String NIT_ANDINA = "800987654-3";

    @Test
    void HU23_compartirConOtraEmpresa_LaInvitadaLoConsultaEnSoloLecturaSinQueSeaSuyo() {
        new LoginPagina(driver, wait).abrir().entrarComo("admin@demo.com", "admin123");
        new ProcesosPagina(driver, wait).abrir()
                .abrirProceso("Aprobación de compras")
                .compartirCon(NIT_ANDINA);

        LoginPagina login = new MenuPrincipal(driver, wait).cerrarSesion();
        InicioPagina inicioAndina = login.entrarComo("admin@andina.com", "admin123");
        // Los datos demo ya compartían "Solicitud de vacaciones": ahora son dos
        assertThat(inicioAndina.valorDe("compartidos")).isEqualTo(2);

        CompartidosPagina compartidos = new CompartidosPagina(driver, wait).abrir();
        assertThat(compartidos.nombres()).contains("Aprobación de compras", "Solicitud de vacaciones");

        ProcesoDetallePagina detalle = compartidos.abrirProceso("Aprobación de compras");
        assertThat(detalle.compartidoPorOtraEmpresa()).isTrue();
        assertThat(detalle.editable()).isFalse();

        assertThat(new ProcesosPagina(driver, wait).abrir().nombres()).doesNotContain("Aprobación de compras");
    }
}

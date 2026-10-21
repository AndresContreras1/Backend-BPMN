package co.edu.javeriana.bpmn.e2e.paginas;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/** /inicio: indicadores de la empresa (HU-07) y accesos según el rol. */
public class InicioPagina extends Pagina {

    // Selectores por id de los elementos de la pantalla de inicio.
    private static final By SALUDO = By.id("saludoInicio");
    private static final By NUEVO_PROCESO = By.id("inicioNuevoProcesoBtn");

    public InicioPagina(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    // Va a /inicio y espera los indicadores.
    public InicioPagina abrir() {
        ir("/inicio");
        return esperarCarga();
    }

    /** Los indicadores aparecen cuando llegan las cuatro consultas del inicio. */
    public InicioPagina esperarCarga() {
        // Basta esperar el indicador total para asumir que cargó el resto.
        visible(indicador("total"));
        return this;
    }

    // Texto del saludo de bienvenida.
    public String saludo() {
        return texto(SALUDO);
    }

    // Lee el número de un indicador (por ejemplo total) y lo convierte a entero.
    public int valorDe(String clave) {
        return Integer.parseInt(texto(indicador(clave)).trim());
    }

    // True si existe el botón de nuevo proceso (depende del rol); no falla si no está.
    public boolean puedeCrearProcesos() {
        return !todos(NUEVO_PROCESO).isEmpty();
    }

    // Pulsa el botón y espera a estar en el formulario de creación.
    public ProcesoFormPagina nuevoProceso() {
        clic(NUEVO_PROCESO);
        esperarRuta("/procesos/nuevo");
        return new ProcesoFormPagina(driver, wait);
    }

    // Arma el selector CSS del valor de un indicador a partir de su clave.
    private static By indicador(String clave) {
        return By.cssSelector("#indicador-" + clave + " .valor-indicador");
    }
}

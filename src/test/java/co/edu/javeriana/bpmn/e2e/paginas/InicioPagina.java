package co.edu.javeriana.bpmn.e2e.paginas;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/** /inicio: indicadores de la empresa (HU-07) y accesos según el rol. */
public class InicioPagina extends Pagina {

    private static final By SALUDO = By.id("saludoInicio");
    private static final By NUEVO_PROCESO = By.id("inicioNuevoProcesoBtn");

    public InicioPagina(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    public InicioPagina abrir() {
        ir("/inicio");
        return esperarCarga();
    }

    /** Los indicadores aparecen cuando llegan las cuatro consultas del inicio. */
    public InicioPagina esperarCarga() {
        visible(indicador("total"));
        return this;
    }

    public String saludo() {
        return texto(SALUDO);
    }

    public int valorDe(String clave) {
        return Integer.parseInt(texto(indicador(clave)).trim());
    }

    public boolean puedeCrearProcesos() {
        return !todos(NUEVO_PROCESO).isEmpty();
    }

    public ProcesoFormPagina nuevoProceso() {
        clic(NUEVO_PROCESO);
        esperarRuta("/procesos/nuevo");
        return new ProcesoFormPagina(driver, wait);
    }

    private static By indicador(String clave) {
        return By.cssSelector("#indicador-" + clave + " .valor-indicador");
    }
}

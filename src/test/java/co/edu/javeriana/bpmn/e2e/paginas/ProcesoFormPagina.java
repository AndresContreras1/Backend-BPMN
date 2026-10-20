package co.edu.javeriana.bpmn.e2e.paginas;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** HU-04: /procesos/nuevo. */
public class ProcesoFormPagina extends Pagina {

    public ProcesoFormPagina(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    /** Llena el formulario, guarda y espera la ficha del proceso creado. */
    public ProcesoDetallePagina crear(String nombre, String categoria, String descripcion) {
        escribir(By.id("nombre"), nombre);
        escribir(By.id("categoria"), categoria);
        escribir(By.id("descripcion"), descripcion);
        clic(By.id("guardarProcesoBtn"));
        wait.until(ExpectedConditions.urlMatches(".*/procesos/\\d+$"));
        return new ProcesoDetallePagina(driver, wait).esperarCarga();
    }
}

package co.edu.javeriana.bpmn.e2e.paginas;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/** HU-06 y HU-07: /procesos en vista de tabla. Las filas están dentro de un @for: se buscan por clase. */
public class ProcesosPagina extends Pagina {

    private static final By FILAS = By.cssSelector("tr.fila-proceso");
    private static final By NOMBRES = By.cssSelector("tr.fila-proceso .nombre-proceso");
    private static final By NUEVO = By.id("nuevoProcesoBtn");

    public ProcesosPagina(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    /** Abre la lista en vista de tabla y espera a que lleguen las filas. */
    public ProcesosPagina abrir() {
        ir("/procesos");
        clic(By.id("vistaTablaBtn"));
        wait.until(ExpectedConditions.presenceOfElementLocated(FILAS));
        return this;
    }

    public List<String> nombres() {
        return todos(NOMBRES).stream().map(WebElement::getText).toList();
    }

    public int cantidad() {
        return todos(FILAS).size();
    }

    /** La lista se vuelve a pedir después de cada cambio: se espera el número, no se lee de una vez. */
    public ProcesosPagina esperarCantidad(int esperados) {
        esperaCon("la lista nunca llegó a " + esperados + " procesos")
                .until(ExpectedConditions.numberOfElementsToBe(FILAS, esperados));
        return this;
    }

    public boolean puedeCrear() {
        return !todos(NUEVO).isEmpty();
    }

    public void filtrarPorEstado(String estado) {
        new Select(visible(By.id("filtroEstado"))).selectByValue(estado);
    }

    public void verEliminados() {
        clic(By.id("verEliminados"));
    }

    /** HU-06: busca la fila por el nombre del proceso (no por su posición), elimina y confirma. */
    public void eliminar(String nombre) {
        WebElement fila = todos(FILAS).stream()
                .filter(f -> f.findElement(By.className("nombre-proceso")).getText().equals(nombre))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No está el proceso " + nombre));
        fila.findElement(By.cssSelector("[id^='eliminarProcesoBtn-']")).click();
        clic(By.id("confirmarEliminarBtn"));
    }

    public ProcesoDetallePagina abrirProceso(String nombre) {
        clic(By.xpath("//tr[contains(@class,'fila-proceso')]//a[contains(@class,'nombre-proceso') and normalize-space()='"
                + nombre + "']"));
        return new ProcesoDetallePagina(driver, wait).esperarCarga();
    }

    public List<String> estados() {
        return todos(By.cssSelector("tr.fila-proceso app-estado-badge")).stream().map(WebElement::getText).toList();
    }
}

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

    // Selectores de las filas de la tabla, de los nombres y del botón de nuevo proceso.
    private static final By FILAS = By.cssSelector("tr.fila-proceso");
    private static final By NOMBRES = By.cssSelector("tr.fila-proceso .nombre-proceso");
    private static final By NUEVO = By.id("nuevoProcesoBtn");

    public ProcesosPagina(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    /** Abre la lista en vista de tabla y espera a que lleguen las filas. */
    // Va a /procesos, cambia a vista de tabla y espera las filas.
    public ProcesosPagina abrir() {
        ir("/procesos");
        clic(By.id("vistaTablaBtn"));
        wait.until(ExpectedConditions.presenceOfElementLocated(FILAS));
        return this;
    }

    // Nombres de proceso visibles en la tabla.
    public List<String> nombres() {
        return todos(NOMBRES).stream().map(WebElement::getText).toList();
    }

    // Cantidad de filas visibles ahora.
    public int cantidad() {
        return todos(FILAS).size();
    }

    /** La lista se vuelve a pedir después de cada cambio: se espera el número, no se lee de una vez. */
    // Espera hasta que haya exactamente ese número de filas (con mensaje claro si falla).
    public ProcesosPagina esperarCantidad(int esperados) {
        esperaCon("la lista nunca llegó a " + esperados + " procesos")
                .until(ExpectedConditions.numberOfElementsToBe(FILAS, esperados));
        return this;
    }

    // True si el botón de nuevo proceso existe para el rol actual.
    public boolean puedeCrear() {
        return !todos(NUEVO).isEmpty();
    }

    // Elige un estado en la lista desplegable del filtro por su valor.
    public void filtrarPorEstado(String estado) {
        new Select(visible(By.id("filtroEstado"))).selectByValue(estado);
    }

    // Activa la opción de ver también los procesos eliminados.
    public void verEliminados() {
        clic(By.id("verEliminados"));
    }

    /** HU-06: busca la fila por el nombre del proceso (no por su posición), elimina y confirma. */
    public void eliminar(String nombre) {
        // Se busca la fila cuyo nombre coincide; si no está, se lanza AssertionError.
        WebElement fila = todos(FILAS).stream()
                .filter(f -> f.findElement(By.className("nombre-proceso")).getText().equals(nombre))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No está el proceso " + nombre));
        // El id del botón empieza por eliminarProcesoBtn- (lleva el id del proceso); luego se confirma.
        fila.findElement(By.cssSelector("[id^='eliminarProcesoBtn-']")).click();
        clic(By.id("confirmarEliminarBtn"));
    }

    // Pulsa el enlace con ese nombre exacto y espera la ficha.
    public ProcesoDetallePagina abrirProceso(String nombre) {
        clic(By.xpath("//tr[contains(@class,'fila-proceso')]//a[contains(@class,'nombre-proceso') and normalize-space()='"
                + nombre + "']"));
        return new ProcesoDetallePagina(driver, wait).esperarCarga();
    }

    // Textos de las insignias de estado de cada fila.
    public List<String> estados() {
        return todos(By.cssSelector("tr.fila-proceso app-estado-badge")).stream().map(WebElement::getText).toList();
    }
}

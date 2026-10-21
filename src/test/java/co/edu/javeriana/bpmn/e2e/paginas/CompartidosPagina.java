package co.edu.javeriana.bpmn.e2e.paginas;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** HU-23: /compartidos, los procesos que otras empresas compartieron en solo lectura. */
public class CompartidosPagina extends Pagina {

    // Selector CSS de los nombres de proceso de cada fila de la tabla.
    private static final By NOMBRES = By.cssSelector("tr.fila-compartido .nombre-proceso");

    public CompartidosPagina(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    // Va a /compartidos y espera a que aparezca al menos una fila.
    public CompartidosPagina abrir() {
        ir("/compartidos");
        wait.until(ExpectedConditions.presenceOfElementLocated(NOMBRES));
        return this;
    }

    // Devuelve los nombres de los procesos compartidos que se ven en la tabla.
    public List<String> nombres() {
        return todos(NOMBRES).stream().map(WebElement::getText).toList();
    }

    // Pulsa el enlace del proceso por su nombre (XPath) y espera la ficha.
    public ProcesoDetallePagina abrirProceso(String nombre) {
        clic(By.xpath("//tr[contains(@class,'fila-compartido')]//a[normalize-space()='" + nombre + "']"));
        return new ProcesoDetallePagina(driver, wait).esperarCarga();
    }
}

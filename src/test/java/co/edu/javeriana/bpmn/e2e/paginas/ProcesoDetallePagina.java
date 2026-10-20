package co.edu.javeriana.bpmn.e2e.paginas;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/** HU-07 y HU-23: la ficha del proceso, con su diagrama en solo lectura y la compartición. */
public class ProcesoDetallePagina extends Pagina {

    private static final By ABRIR_DIAGRAMA = By.id("abrirDiagramaBtn");

    public ProcesoDetallePagina(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    public ProcesoDetallePagina abrir(long id) {
        ir("/procesos/" + id);
        return esperarCarga();
    }

    public ProcesoDetallePagina esperarCarga() {
        visible(ABRIR_DIAGRAMA);
        return this;
    }

    public String titulo() {
        return texto(By.tagName("h1"));
    }

    public String estado() {
        return texto(By.cssSelector("dl app-estado-badge .badge"));
    }

    public long id() {
        String url = driver.getCurrentUrl();
        return Long.parseLong(url.substring(url.lastIndexOf('/') + 1));
    }

    /** Los botones de edición solo aparecen si el usuario puede cambiar el proceso. */
    public boolean editable() {
        return !todos(By.id("editarProcesoBtn")).isEmpty();
    }

    public boolean compartidoPorOtraEmpresa() {
        return !todos(By.id("procesoCompartidoMsg")).isEmpty();
    }

    /** HU-23: activa la compartición, agrega el NIT y guarda. */
    public ProcesoDetallePagina compartirCon(String nit) {
        By interruptor = By.id("compartidoSwitch");
        if (!visible(interruptor).isSelected()) {
            clic(interruptor);
        }
        escribir(By.id("nuevoNit"), nit);
        clic(By.id("agregarNitBtn"));
        clic(By.id("guardarComparticionBtn"));
        visible(By.id("avisoComparticion"));
        return this;
    }
}

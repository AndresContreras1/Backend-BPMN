package co.edu.javeriana.bpmn.e2e.paginas;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/** HU-07 y HU-23: la ficha del proceso, con su diagrama en solo lectura y la compartición. */
public class ProcesoDetallePagina extends Pagina {

    // Botón que solo existe cuando la ficha terminó de cargar.
    private static final By ABRIR_DIAGRAMA = By.id("abrirDiagramaBtn");

    public ProcesoDetallePagina(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    // Va a /procesos/<id> y espera la ficha.
    public ProcesoDetallePagina abrir(long id) {
        ir("/procesos/" + id);
        return esperarCarga();
    }

    // La ficha está lista cuando el botón de abrir diagrama es visible.
    public ProcesoDetallePagina esperarCarga() {
        visible(ABRIR_DIAGRAMA);
        return this;
    }

    // Texto del encabezado h1 (nombre del proceso).
    public String titulo() {
        return texto(By.tagName("h1"));
    }

    // Texto de la insignia de estado (por ejemplo BORRADOR).
    public String estado() {
        return texto(By.cssSelector("dl app-estado-badge .badge"));
    }

    // Lee el id del final de la URL actual.
    public long id() {
        String url = driver.getCurrentUrl();
        return Long.parseLong(url.substring(url.lastIndexOf('/') + 1));
    }

    /** Los botones de edición solo aparecen si el usuario puede cambiar el proceso. */
    public boolean editable() {
        // Si el botón existe, el usuario puede editar.
        return !todos(By.id("editarProcesoBtn")).isEmpty();
    }

    // True si se muestra el mensaje de que el proceso es de otra empresa.
    public boolean compartidoPorOtraEmpresa() {
        return !todos(By.id("procesoCompartidoMsg")).isEmpty();
    }

    /** HU-23: activa la compartición, agrega el NIT y guarda. */
    public ProcesoDetallePagina compartirCon(String nit) {
        // Si el interruptor de compartir está apagado, se enciende.
        By interruptor = By.id("compartidoSwitch");
        if (!visible(interruptor).isSelected()) {
            clic(interruptor);
        }
        // Se escribe el NIT, se agrega a la lista, se guarda y se espera el aviso de confirmación.
        escribir(By.id("nuevoNit"), nit);
        clic(By.id("agregarNitBtn"));
        clic(By.id("guardarComparticionBtn"));
        visible(By.id("avisoComparticion"));
        return this;
    }
}

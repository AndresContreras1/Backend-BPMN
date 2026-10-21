package co.edu.javeriana.bpmn.e2e.paginas;

import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Page Object base: cada pantalla del frontend es una clase con sus selectores y acciones, así las pruebas
 * se leen como el caso de uso y un cambio en el HTML se arregla en un solo lugar.
 * Toda lectura espera primero: en una SPA el HTML llega antes que los datos.
 */
public abstract class Pagina {

    /** Dónde corre el frontend: -De2e.url, por defecto el ng serve de desarrollo. */
    // Configurable por propiedad del sistema; si no se da, usa el servidor de desarrollo de Angular.
    public static final String URL_FRONTEND = System.getProperty("e2e.url", "http://localhost:4200");
    /** Máximo que se espera a que algo ocurra en la página antes de dar la prueba por fallida. */
    public static final Duration ESPERA = Duration.ofSeconds(10);

    // Navegador y espera compartidos que cada página recibe de la prueba.
    protected final WebDriver driver;
    protected final WebDriverWait wait;

    protected Pagina(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    // Espera hasta que el elemento exista y sea visible; falla con timeout si no aparece.
    protected WebElement visible(By selector) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(selector));
    }

    /** Espera a que se pueda pulsar, lo lleva al centro de la pantalla (lejos del navbar fijo) y hace clic. */
    protected void clic(By selector) {
        // Espera a que el elemento esté visible y habilitado antes de pulsarlo.
        WebElement elemento = wait.until(ExpectedConditions.elementToBeClickable(selector));
        centrar(elemento);
        elemento.click();
    }

    /**
     * Bootstrap pone scroll-behavior: smooth en la página: el desplazamiento se anima y Selenium haría clic
     * antes de que el elemento llegue. Con behavior 'instant' el elemento ya está en su sitio al hacer clic.
     */
    // Ejecuta JavaScript para desplazar el elemento al centro de la pantalla sin animación.
    protected void centrar(WebElement elemento) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block: 'center', behavior: 'instant'});", elemento);
    }

    /** Selecciona todo lo que tenga el campo y lo reemplaza: funciona también con formularios reactivos. */
    protected void escribir(By selector, String texto) {
        WebElement campo = visible(selector);
        // Ctrl+A selecciona el contenido previo y al escribir se reemplaza.
        campo.sendKeys(Keys.chord(Keys.CONTROL, "a"), texto);
    }

    protected String texto(By selector) {
        return visible(selector).getText();
    }

    // findElements no espera ni falla si no hay nada: devuelve una lista vacía (útil para preguntar si algo existe).
    protected List<WebElement> todos(By selector) {
        return driver.findElements(selector);
    }

    protected void ir(String ruta) {
        driver.get(URL_FRONTEND + ruta);
    }

    /** Una espera con su propio mensaje, sin cambiar la espera compartida de la prueba. */
    // Crea una espera nueva con mensaje de error propio para saber qué no ocurrió.
    protected WebDriverWait esperaCon(String mensaje) {
        WebDriverWait espera = new WebDriverWait(driver, ESPERA);
        espera.withMessage(mensaje);
        return espera;
    }

    // Espera a que la URL contenga el fragmento, señal de que la navegación terminó.
    protected void esperarRuta(String fragmento) {
        wait.until(ExpectedConditions.urlContains(fragmento));
    }

    /** Cierra los avisos (toasts) de la esquina inferior para que no tapen botones. */
    public void cerrarAvisos() {
        // Se pulsa el botón de cierre de cada aviso visible.
        for (WebElement cerrar : todos(By.cssSelector(".notificacion .btn-close"))) {
            if (cerrar.isDisplayed()) {
                cerrar.click();
            }
        }
        // Se espera a que desaparezcan todos los avisos (cero elementos).
        wait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector(".notificacion"), 0));
    }
}

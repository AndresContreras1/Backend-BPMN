package co.edu.javeriana.bpmn.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInfo;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import co.edu.javeriana.bpmn.e2e.paginas.Pagina;

/**
 * Base de las pruebas de sistema: Selenium maneja un Chrome de verdad sobre el frontend (Angular) y este
 * levanta el backend completo en su puerto fijo (8090), que es al que apunta el proxy de Angular.
 *
 * - Perfil test + demo: base en memoria propia con las empresas y usuarios demo del DataLoader.
 * - @DirtiesContext antes de cada prueba: cada una arranca con los datos demo limpios y no depende de otra.
 * - @Tag("e2e"): no corren con el ./mvnw verify normal; se corren con ./mvnw verify -Pe2e y el frontend arriba.
 * - Selenium Manager (Selenium 4.6+) descarga el chromedriver que corresponde al Chrome instalado.
 *
 * Propiedades: -De2e.url (por defecto http://localhost:4200) y -De2e.headless=false para ver el navegador.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:e2edb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("e2e")
public abstract class SistemaBase {

    private static final Path CAPTURAS = Path.of("target", "capturas");

    protected WebDriver driver;
    protected WebDriverWait wait;

    @BeforeEach
    void abrirNavegador() {
        ChromeOptions opciones = new ChromeOptions();
        opciones.addArguments("--disable-notifications", "--disable-extensions", "--lang=es");
        // Con una ventana pequeña el menú colapsa y los botones que busca la prueba quedan ocultos
        opciones.addArguments("--window-size=1920,1080");
        if (Boolean.parseBoolean(System.getProperty("e2e.headless", "true"))) {
            opciones.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage");
        }
        driver = new ChromeDriver(opciones);
        wait = new WebDriverWait(driver, Pagina.ESPERA);
    }

    /** Guarda cómo quedó la pantalla (sirve para entender un fallo) y cierra el navegador y su proceso. */
    @AfterEach
    void cerrarNavegador(TestInfo prueba) throws IOException {
        if (driver instanceof TakesScreenshot camara) {
            Files.createDirectories(CAPTURAS);
            String nombre = getClass().getSimpleName() + "-" + prueba.getTestMethod().map(m -> m.getName()).orElse("prueba");
            Files.copy(camara.getScreenshotAs(OutputType.FILE).toPath(), CAPTURAS.resolve(nombre + ".png"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        driver.quit();
    }

    protected void abrir(String ruta) {
        driver.get(Pagina.URL_FRONTEND + ruta);
    }
}

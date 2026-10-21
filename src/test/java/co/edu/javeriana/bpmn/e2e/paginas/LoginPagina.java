package co.edu.javeriana.bpmn.e2e.paginas;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/** HU-03: /login. */
public class LoginPagina extends Pagina {

    private static final By CORREO = By.id("correo");
    private static final By PASSWORD = By.id("password");
    private static final By INGRESAR = By.id("loginBtn");
    private static final By ERROR = By.cssSelector("app-alerta-error .alert-danger");

    public LoginPagina(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    public LoginPagina abrir() {
        ir("/login");
        visible(CORREO);
        return this;
    }

    /** Inicia sesión y espera el inicio de la empresa ya cargado. */
    public InicioPagina entrarComo(String correo, String password) {
        intentar(correo, password);
        esperarRuta("/inicio");
        InicioPagina inicio = new InicioPagina(driver, wait).esperarCarga();
        inicio.cerrarAvisos();
        return inicio;
    }

    public LoginPagina intentar(String correo, String password) {
        escribir(CORREO, correo);
        escribir(PASSWORD, password);
        clic(INGRESAR);
        return this;
    }

    public String error() {
        return texto(ERROR);
    }
}

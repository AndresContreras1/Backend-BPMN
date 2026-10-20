package co.edu.javeriana.bpmn.e2e.paginas;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/** La barra de navegación: enlaces del menú y el menú desplegable del usuario. */
public class MenuPrincipal extends Pagina {

    private static final By MENU_USUARIO = By.id("menuUsuarioBtn");
    private static final By CERRAR_SESION = By.id("cerrarSesionBtn");

    public MenuPrincipal(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    public String usuario() {
        return texto(By.id("usuarioNombre"));
    }

    public String rol() {
        clic(MENU_USUARIO);
        String rol = texto(By.id("usuarioRol"));
        clic(MENU_USUARIO);
        return rol;
    }

    /** HU-03: abre el menú del usuario, cierra la sesión y espera el login. */
    public LoginPagina cerrarSesion() {
        cerrarAvisos();
        clic(MENU_USUARIO);
        clic(CERRAR_SESION);
        esperarRuta("/login");
        return new LoginPagina(driver, wait);
    }
}

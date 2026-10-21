package co.edu.javeriana.bpmn.e2e.paginas;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/** La barra de navegación: enlaces del menú y el menú desplegable del usuario. */
public class MenuPrincipal extends Pagina {

    // Selectores del menú desplegable del usuario.
    private static final By MENU_USUARIO = By.id("menuUsuarioBtn");
    private static final By CERRAR_SESION = By.id("cerrarSesionBtn");

    public MenuPrincipal(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    // Nombre del usuario que se muestra en la barra.
    public String usuario() {
        return texto(By.id("usuarioNombre"));
    }

    // Abre el menú, lee el rol y lo vuelve a cerrar.
    public String rol() {
        clic(MENU_USUARIO);
        String rol = texto(By.id("usuarioRol"));
        clic(MENU_USUARIO);
        return rol;
    }

    /** HU-03: abre el menú del usuario, cierra la sesión y espera el login. */
    public LoginPagina cerrarSesion() {
        // Primero se limpian los avisos para que no tapen el menú.
        cerrarAvisos();
        clic(MENU_USUARIO);
        clic(CERRAR_SESION);
        // Después de cerrar sesión se espera la redirección a /login.
        esperarRuta("/login");
        return new LoginPagina(driver, wait);
    }
}

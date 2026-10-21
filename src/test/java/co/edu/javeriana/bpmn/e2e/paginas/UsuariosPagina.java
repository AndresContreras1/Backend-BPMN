package co.edu.javeriana.bpmn.e2e.paginas;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/** HU-02: /usuarios y el formulario para invitar (solo el administrador). */
public class UsuariosPagina extends Pagina {

    private static final By CORREOS = By.cssSelector("tr.fila-usuario .correo-usuario");

    public UsuariosPagina(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    public UsuariosPagina abrir() {
        ir("/usuarios");
        wait.until(ExpectedConditions.presenceOfElementLocated(CORREOS));
        return this;
    }

    public List<String> correos() {
        return todos(CORREOS).stream().map(WebElement::getText).toList();
    }

    /** Invita a un usuario con su rol y contraseña inicial y vuelve a la lista con el aviso. */
    public UsuariosPagina invitar(String nombre, String correo, String rol, String passwordInicial) {
        clic(By.id("invitarUsuarioBtn"));
        esperarRuta("/usuarios/nuevo");
        escribir(By.id("nombre"), nombre);
        escribir(By.id("correo"), correo);
        new Select(visible(By.id("rol"))).selectByValue(rol);
        escribir(By.id("passwordInicial"), passwordInicial);
        clic(By.id("guardarUsuarioBtn"));
        visible(By.id("usuarioCreadoMsg"));
        wait.until(ExpectedConditions.presenceOfElementLocated(CORREOS));
        return this;
    }
}

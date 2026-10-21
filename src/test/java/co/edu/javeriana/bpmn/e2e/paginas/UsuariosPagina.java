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

    // Selector de la celda de correo de cada fila de usuarios.
    private static final By CORREOS = By.cssSelector("tr.fila-usuario .correo-usuario");

    public UsuariosPagina(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    // Va a /usuarios y espera al menos una fila.
    public UsuariosPagina abrir() {
        ir("/usuarios");
        wait.until(ExpectedConditions.presenceOfElementLocated(CORREOS));
        return this;
    }

    // Correos de los usuarios listados.
    public List<String> correos() {
        return todos(CORREOS).stream().map(WebElement::getText).toList();
    }

    /** Invita a un usuario con su rol y contraseña inicial y vuelve a la lista con el aviso. */
    // Flujo: abrir el formulario, llenar campos, elegir rol en la lista, guardar y esperar aviso y lista.
    public UsuariosPagina invitar(String nombre, String correo, String rol, String passwordInicial) {
        clic(By.id("invitarUsuarioBtn"));
        esperarRuta("/usuarios/nuevo");
        escribir(By.id("nombre"), nombre);
        escribir(By.id("correo"), correo);
        // Select maneja listas desplegables: elige la opción por su valor.
        new Select(visible(By.id("rol"))).selectByValue(rol);
        escribir(By.id("passwordInicial"), passwordInicial);
        clic(By.id("guardarUsuarioBtn"));
        visible(By.id("usuarioCreadoMsg"));
        wait.until(ExpectedConditions.presenceOfElementLocated(CORREOS));
        return this;
    }
}

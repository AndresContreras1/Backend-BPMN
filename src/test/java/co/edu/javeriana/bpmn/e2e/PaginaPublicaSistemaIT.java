package co.edu.javeriana.bpmn.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import co.edu.javeriana.bpmn.e2e.paginas.LoginPagina;

/** Casos sencillos: abrir una URL y comprobar lo que se ve, sin sesión. */
// Suite de sistema de las páginas que se ven sin iniciar sesión (bienvenida y login).
class PaginaPublicaSistemaIT extends SistemaBase {

    // Escenario: abrir la raíz del sitio.
    // Esperado: se ve el título y el diagrama de ejemplo con 9 nodos, y el botón lleva al login.
    @Test
    void Bienvenida_abrir_MuestraElEjemploDelEnunciadoYLlevaAlLogin() {
        // Actuar: abrir la portada. Se espera a que el lienzo del diagrama sea visible.
        abrir("/");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("lienzoDiagrama")));

        // Comprobar: el título principal y la cantidad de nodos del ejemplo.
        assertThat(driver.findElement(By.tagName("h1")).getText()).contains("Modele los procesos de su empresa");
        assertThat(driver.findElements(By.cssSelector("#lienzoDiagrama .nodo"))).hasSize(9);

        // Actuar: pulsar el botón de comenzar; luego se espera la URL /login y se comprueba el título de la pestaña.
        driver.findElement(By.id("empezarLoginBtn")).click();
        wait.until(ExpectedConditions.urlContains("/login"));
        assertThat(driver.getTitle()).isEqualTo("Iniciar sesión · Editor BPMN");
    }

    // Escenario: iniciar sesión con una clave errónea.
    // Esperado: mensaje genérico (sin revelar si el correo existe) y la URL sigue en /login.
    @Test
    void HU03_credencialesIncorrectas_MensajeGenericoYNoEntra() {
        // Actuar: abrir el login e intentar entrar con credenciales malas.
        LoginPagina login = new LoginPagina(driver, wait).abrir().intentar("admin@demo.com", "no-es-la-clave");

        assertThat(login.error()).contains("Correo o contraseña incorrectos");
        assertThat(driver.getCurrentUrl()).endsWith("/login");
    }

    // Escenario: abrir una ruta protegida sin sesión.
    // Esperado: redirección al login con la ruta original guardada en returnUrl.
    @Test
    void HU03_rutaInternaSinSesion_LlevaAlLoginRecordandoLaRuta() {
        // Actuar: pedir /procesos sin sesión.
        abrir("/procesos");

        wait.until(ExpectedConditions.urlContains("/login"));
        // Comprobar: %2F es la barra / codificada en la URL.
        assertThat(driver.getCurrentUrl()).contains("returnUrl=%2Fprocesos");
    }
}

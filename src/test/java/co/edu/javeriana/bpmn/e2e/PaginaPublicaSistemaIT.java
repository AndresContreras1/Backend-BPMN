package co.edu.javeriana.bpmn.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import co.edu.javeriana.bpmn.e2e.paginas.LoginPagina;

/** Casos sencillos: abrir una URL y comprobar lo que se ve, sin sesión. */
class PaginaPublicaSistemaIT extends SistemaBase {

    @Test
    void Bienvenida_abrir_MuestraElEjemploDelEnunciadoYLlevaAlLogin() {
        abrir("/");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("lienzoDiagrama")));

        assertThat(driver.findElement(By.tagName("h1")).getText()).contains("Modele los procesos de su empresa");
        assertThat(driver.findElements(By.cssSelector("#lienzoDiagrama .nodo"))).hasSize(9);

        driver.findElement(By.id("empezarLoginBtn")).click();
        wait.until(ExpectedConditions.urlContains("/login"));
        assertThat(driver.getTitle()).isEqualTo("Iniciar sesión · Editor BPMN");
    }

    @Test
    void HU03_credencialesIncorrectas_MensajeGenericoYNoEntra() {
        LoginPagina login = new LoginPagina(driver, wait).abrir().intentar("admin@demo.com", "no-es-la-clave");

        assertThat(login.error()).contains("Correo o contraseña incorrectos");
        assertThat(driver.getCurrentUrl()).endsWith("/login");
    }

    @Test
    void HU03_rutaInternaSinSesion_LlevaAlLoginRecordandoLaRuta() {
        abrir("/procesos");

        wait.until(ExpectedConditions.urlContains("/login"));
        assertThat(driver.getCurrentUrl()).contains("returnUrl=%2Fprocesos");
    }
}

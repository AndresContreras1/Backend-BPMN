package co.edu.javeriana.bpmn.e2e.paginas;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/** HU-01: /registro. Quien registra la empresa queda como administrador y entra de una vez. */
public class RegistroPagina extends Pagina {

    public RegistroPagina(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    // Va a /registro y espera el campo nombre.
    public RegistroPagina abrir() {
        ir("/registro");
        visible(By.id("nombre"));
        return this;
    }

    // Llena los cinco campos, envía y espera quedar en /inicio con la empresa cargada.
    public InicioPagina registrar(String empresa, String nit, String representante, String correo, String password) {
        escribir(By.id("nombre"), empresa);
        escribir(By.id("nit"), nit);
        escribir(By.id("nombreRepresentante"), representante);
        escribir(By.id("correoContacto"), correo);
        escribir(By.id("password"), password);
        clic(By.id("registrarEmpresaBtn"));
        esperarRuta("/inicio");
        InicioPagina inicio = new InicioPagina(driver, wait).esperarCarga();
        inicio.cerrarAvisos();
        return inicio;
    }
}

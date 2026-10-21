package co.edu.javeriana.bpmn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del backend. @SpringBootApplication activa la configuración automática de Spring Boot
 * y el escaneo de componentes (controladores, servicios, repositorios y configuraciones) de este paquete y sus subpaquetes.
 */
@SpringBootApplication
public class BackendBpmnApplication {

    /** Arranca el servidor web embebido y el contexto de Spring. */
    public static void main(String[] args) {
        SpringApplication.run(BackendBpmnApplication.class, args);
    }
}

package co.edu.javeriana.bpmn.soporte;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Ejecuta la prueba como un usuario autenticado de una empresa, sin pasar por el login.
 * Útil en pruebas de controlador (@WebMvcTest) donde el servicio es un mock.
 */
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = ConEmpresaSecurityContextFactory.class)
public @interface ConEmpresa {

    long empresa() default 1L;

    long usuario() default 10L;

    String rol() default "EDITOR";

    String correo() default "ana@empresa-a.com";
}

package co.edu.javeriana.bpmn.soporte;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.security.ApiPrincipal;

/**
 * Fábrica que usa Spring Security Test para construir el usuario autenticado que describe @ConEmpresa.
 */
public class ConEmpresaSecurityContextFactory implements WithSecurityContextFactory<ConEmpresa> {

    @Override
    public SecurityContext createSecurityContext(ConEmpresa anotacion) {
        // Se crea el usuario autenticado (principal) con los datos de la anotación.
        ApiPrincipal principal = new ApiPrincipal(anotacion.usuario(), anotacion.empresa(),
                anotacion.correo(), RolAcceso.valueOf(anotacion.rol()));
        // Se arma un contexto de seguridad vacío y se le asigna la autenticación.
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        // credentials en null: el principal nunca lleva la contraseña
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return contexto;
    }
}

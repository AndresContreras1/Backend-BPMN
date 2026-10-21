package co.edu.javeriana.bpmn.soporte;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.security.ApiPrincipal;

public class ConEmpresaSecurityContextFactory implements WithSecurityContextFactory<ConEmpresa> {

    @Override
    public SecurityContext createSecurityContext(ConEmpresa anotacion) {
        ApiPrincipal principal = new ApiPrincipal(anotacion.usuario(), anotacion.empresa(),
                anotacion.correo(), RolAcceso.valueOf(anotacion.rol()));
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        // credentials en null: el principal nunca lleva la contraseña
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return contexto;
    }
}

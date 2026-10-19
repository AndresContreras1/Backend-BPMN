package co.edu.javeriana.bpmn.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Acceso al usuario autenticado para los casos sueltos que no reciben el principal por parámetro
 * (autor de la auditoría y del historial). Los servicios reciben el empresaId explícito.
 */
@Component
public class ContextoDeSeguridad {

    public Optional<ApiPrincipal> actual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof ApiPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }
}

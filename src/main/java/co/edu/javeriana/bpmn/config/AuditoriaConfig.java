package co.edu.javeriana.bpmn.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import co.edu.javeriana.bpmn.security.ApiPrincipal;

/**
 * Activa la auditoría de JPA: fechas de creación y modificación y el autor de cada cambio.
 * El autor es el correo del usuario autenticado; sin sesión (registro, inicializador) es "sistema".
 */
@Configuration
@EnableJpaAuditing
public class AuditoriaConfig {

    /** Nombre que se guarda como autor cuando no hay un usuario autenticado. */
    public static final String AUDITOR_SISTEMA = "sistema";

    /** Le dice a JPA quién es el autor del cambio actual (alimenta creadoPor y modificadoPor). */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            // Se consulta el usuario autenticado de la petición en curso.
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof ApiPrincipal principal) {
                // Con usuario autenticado, el autor es su correo.
                return Optional.of(principal.correo());
            }
            // Sin sesión (registro de empresa, DataLoader) el autor es "sistema".
            return Optional.of(AUDITOR_SISTEMA);
        };
    }
}

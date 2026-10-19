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

    public static final String AUDITOR_SISTEMA = "sistema";

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof ApiPrincipal principal) {
                return Optional.of(principal.correo());
            }
            return Optional.of(AUDITOR_SISTEMA);
        };
    }
}

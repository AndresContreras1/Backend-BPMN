package co.edu.javeriana.bpmn.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Activa la auditoría de JPA: fechas de creación y modificación y el autor de cada cambio.
 */
@Configuration
@EnableJpaAuditing
public class AuditoriaConfig {

    public static final String AUDITOR_SISTEMA = "sistema";

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(AUDITOR_SISTEMA);
    }
}

package co.edu.javeriana.bpmn.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;

/**
 * Documentación OpenAPI. Swagger UI queda en /swagger-ui/index.html y el contrato en /v3/api-docs.
 * El esquema bearerAuth habilita el botón Authorize para probar los endpoints protegidos con el JWT.
 */
@Configuration
@SecurityScheme(name = OpenApiConfig.ESQUEMA_SEGURIDAD, type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    /** Nombre del esquema de seguridad; los controladores lo referencian para marcar sus endpoints como protegidos. */
    public static final String ESQUEMA_SEGURIDAD = "bearerAuth";

    /** Datos generales de la documentación (título, versión, descripción) y requisito de seguridad global. */
    @Bean
    OpenAPI apiInfo() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Editor de procesos BPMN")
                        .version("v1")
                        .description("Gestión multiempresa de procesos y modelado BPMN: empresas, usuarios, "
                                + "procesos, roles de proceso, pools, lanes, actividades, gateways, arcos, "
                                + "mensajes y correlación. El sistema modela y valida; no ejecuta procesos."))
                // Por defecto todos los endpoints piden el token; Swagger UI lo envía como Authorization: Bearer.
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_SEGURIDAD));
    }
}

package co.edu.javeriana.bpmn.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;
import co.edu.javeriana.bpmn.security.JwtService;

/**
 * Pruebas unitarias de JwtService (tokens de sesión): emisión, validación y errores de configuración.
 * Se crea el servicio a mano, sin Spring.
 */
class JwtServiceTest {

    // Clave de firma de 64 bytes en Base64 (solo para pruebas).
    private static final String SECRETO = Base64.getEncoder().encodeToString(new byte[64]);

    // Usuario de ejemplo con id 7 y empresa 3 para emitir tokens.
    private static Usuario usuario() {
        Usuario u = Usuario.builder().id(7L).nombre("Ana").correo("ana@a.com").rol(RolAcceso.EDITOR).build();
        u.setEmpresaId(3L);
        return u;
    }

    // Escenario: validar un token recién generado.
    // Esperado: es válido y devuelve el id del usuario (7).
    @Test
    void JwtService_tokenRecienEmitido_DevuelveElIdDelUsuario() {
        // Preparar: servicio con expiración de 1800 segundos.
        JwtService jwt = new JwtService(SECRETO, 1800);

        assertThat(jwt.usuarioIdSiEsValido(jwt.generarToken(usuario()))).contains(7L);
    }

    // Escenario: token emitido hace 120 s con vigencia de 60 s.
    // Esperado: no es válido (resultado vacío).
    @Test
    void JwtService_tokenExpirado_NoEsValido() {
        JwtService jwt = new JwtService(SECRETO, 60);
        // Se emite el token con una fecha de emisión en el pasado para simular que ya expiró.
        String token = jwt.generarToken(usuario(), Instant.now().minusSeconds(120));

        assertThat(jwt.usuarioIdSiEsValido(token)).isEmpty();
    }

    // Escenario: token firmado con otra clave.
    // Esperado: la firma no coincide y no es válido.
    @Test
    void JwtService_tokenDeOtraLlave_NoEsValido() {
        // Se genera el token con un servicio de clave distinta (secreto vacío) y se valida con el servicio real.
        String token = new JwtService("", 1800).generarToken(usuario());

        assertThat(new JwtService(SECRETO, 1800).usuarioIdSiEsValido(token)).isEmpty();
    }

    // Escenario: textos que no son tokens (basura y cadena vacía).
    // Esperado: ambos inválidos, sin lanzar excepción.
    @Test
    void JwtService_textoQueNoEsToken_NoEsValido() {
        JwtService jwt = new JwtService(SECRETO, 1800);

        assertThat(jwt.usuarioIdSiEsValido("abc.def.ghi")).isEmpty();
        assertThat(jwt.usuarioIdSiEsValido("")).isEmpty();
    }

    // Escenario: configurar un secreto que no es Base64.
    // Esperado: IllegalStateException al construir, con mensaje que menciona Base64.
    @Test
    void JwtService_secretoQueNoEsBase64_FallaAlArrancar() {
        assertThatThrownBy(() -> new JwtService("esto no es base64 !!", 1800))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    // Escenario: secreto de solo 16 bytes (128 bits).
    // Esperado: falla al arrancar pidiendo 256 bits.
    @Test
    void JwtService_secretoCorto_FallaAlArrancar() {
        String corto = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new JwtService(corto, 1800))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    // Escenario: crear el servicio con 900 s.
    // Esperado: el getter devuelve 900.
    @Test
    void JwtService_expiracionConfigurable() {
        assertThat(new JwtService(SECRETO, 900).getExpiracionSegundos()).isEqualTo(900);
    }
}

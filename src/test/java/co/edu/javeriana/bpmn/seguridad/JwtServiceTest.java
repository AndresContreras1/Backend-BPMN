package co.edu.javeriana.bpmn.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;
import co.edu.javeriana.bpmn.security.JwtService;

class JwtServiceTest {

    private static final String SECRETO = Base64.getEncoder().encodeToString(new byte[64]);

    private static Usuario usuario() {
        Usuario u = Usuario.builder().id(7L).nombre("Ana").correo("ana@a.com").rol(RolAcceso.EDITOR).build();
        u.setEmpresaId(3L);
        return u;
    }

    @Test
    void JwtService_tokenRecienEmitido_DevuelveElIdDelUsuario() {
        JwtService jwt = new JwtService(SECRETO, 1800);

        assertThat(jwt.usuarioIdSiEsValido(jwt.generarToken(usuario()))).contains(7L);
    }

    @Test
    void JwtService_tokenExpirado_NoEsValido() {
        JwtService jwt = new JwtService(SECRETO, 60);
        String token = jwt.generarToken(usuario(), Instant.now().minusSeconds(120));

        assertThat(jwt.usuarioIdSiEsValido(token)).isEmpty();
    }

    @Test
    void JwtService_tokenDeOtraLlave_NoEsValido() {
        String token = new JwtService("", 1800).generarToken(usuario());

        assertThat(new JwtService(SECRETO, 1800).usuarioIdSiEsValido(token)).isEmpty();
    }

    @Test
    void JwtService_textoQueNoEsToken_NoEsValido() {
        JwtService jwt = new JwtService(SECRETO, 1800);

        assertThat(jwt.usuarioIdSiEsValido("abc.def.ghi")).isEmpty();
        assertThat(jwt.usuarioIdSiEsValido("")).isEmpty();
    }

    @Test
    void JwtService_secretoQueNoEsBase64_FallaAlArrancar() {
        assertThatThrownBy(() -> new JwtService("esto no es base64 !!", 1800))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void JwtService_secretoCorto_FallaAlArrancar() {
        String corto = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new JwtService(corto, 1800))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void JwtService_expiracionConfigurable() {
        assertThat(new JwtService(SECRETO, 900).getExpiracionSegundos()).isEqualTo(900);
    }
}

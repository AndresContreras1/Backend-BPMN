package co.edu.javeriana.bpmn.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.gestion.dto.LoginRequest;
import co.edu.javeriana.bpmn.gestion.dto.LoginResponse;
import co.edu.javeriana.bpmn.gestion.entity.Empresa;
import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;
import co.edu.javeriana.bpmn.gestion.repository.UsuarioRepository;
import co.edu.javeriana.bpmn.gestion.service.impl.AuthServiceImpl;
import co.edu.javeriana.bpmn.security.JwtService;

/**
 * Pruebas unitarias de AuthServiceImpl (login y datos del usuario actual) con Mockito:
 * todas las dependencias son dobles, no hay Spring ni base de datos.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    // Dobles de las dependencias del servicio.
    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EmpresaService empresaService;

    // Objeto bajo prueba, con los mocks inyectados.
    @InjectMocks
    private AuthServiceImpl authService;

    // Usuario de ejemplo (id 7, empresa 3, rol EDITOR).
    private static Usuario ana() {
        Usuario u = Usuario.builder().id(7L).nombre("Ana").correo("ana@a.com").rol(RolAcceso.EDITOR).build();
        u.setEmpresaId(3L);
        return u;
    }

    // Escenario: login con credenciales correctas (el correo llega con espacios y mayúsculas).
    // Esperado: respuesta con token, tipo Bearer y nombre de empresa.
    @Test
    void AuthService_login_CredencialesValidasDevuelveToken() {
        // Preparar: los mocks simulan autenticación correcta, usuario existente, token "jwt", expiración y empresa.
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken("ana@a.com", null, java.util.List.of()));
        when(usuarioRepository.findByCorreoIgnoreCase("ana@a.com")).thenReturn(Optional.of(ana()));
        when(jwtService.generarToken(any(Usuario.class))).thenReturn("jwt");
        when(jwtService.getExpiracionSegundos()).thenReturn(1800L);
        when(empresaService.obtenerEntidad(3L)).thenReturn(Empresa.builder().id(3L).nombre("Empresa A").build());

        // Actuar: login con correo " ANA@a.com ".
        LoginResponse respuesta = authService.login(new LoginRequest(" ANA@a.com ", "clave"));

        // Comprobar la respuesta.
        assertThat(respuesta.token()).isEqualTo("jwt");
        assertThat(respuesta.tipo()).isEqualTo("Bearer");
        assertThat(respuesta.usuario().empresaNombre()).isEqualTo("Empresa A");
        // verify con argThat: se autenticó con el correo ya normalizado (sin espacios, en minúscula).
        verify(authenticationManager).authenticate(org.mockito.ArgumentMatchers.argThat(a ->
                a.getPrincipal().equals("ana@a.com")));
    }

    // Escenario: el AuthenticationManager rechaza las credenciales.
    // Esperado: la excepción se propaga y nunca se genera un token.
    @Test
    void AuthService_login_CredencialesInvalidasPropagaLaExcepcionYNoEmiteToken() {
        // thenThrow: el mock lanza BadCredentialsException.
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("mal"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@a.com", "mala")))
                .isInstanceOf(BadCredentialsException.class);
        // never(): confirma que generarToken no se llamó.
        verify(jwtService, never()).generarToken(any());
    }

    // Escenario: pedir el usuario 7 con una empresa que no es la suya.
    // Esperado: NotFoundException (404) para no revelar que existe.
    @Test
    void AuthService_yo_UsuarioDeOtraEmpresaEs404() {
        when(usuarioRepository.findByIdAndEmpresaId(7L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.yo(7L, 99L)).isInstanceOf(NotFoundException.class);
    }

    // Escenario: pedir el usuario propio.
    // Esperado: resumen con el correo correcto.
    @Test
    void AuthService_yo_DevuelveResumenConEmpresa() {
        when(usuarioRepository.findByIdAndEmpresaId(7L, 3L)).thenReturn(Optional.of(ana()));
        when(empresaService.obtenerEntidad(3L)).thenReturn(Empresa.builder().id(3L).nombre("Empresa A").build());

        assertThat(authService.yo(7L, 3L).correo()).isEqualTo("ana@a.com");
    }
}

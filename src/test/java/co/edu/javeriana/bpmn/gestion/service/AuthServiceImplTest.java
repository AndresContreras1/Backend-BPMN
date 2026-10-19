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

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EmpresaService empresaService;

    @InjectMocks
    private AuthServiceImpl authService;

    private static Usuario ana() {
        Usuario u = Usuario.builder().id(7L).nombre("Ana").correo("ana@a.com").rol(RolAcceso.EDITOR).build();
        u.setEmpresaId(3L);
        return u;
    }

    @Test
    void AuthService_login_CredencialesValidasDevuelveToken() {
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken("ana@a.com", null, java.util.List.of()));
        when(usuarioRepository.findByCorreoIgnoreCase("ana@a.com")).thenReturn(Optional.of(ana()));
        when(jwtService.generarToken(any(Usuario.class))).thenReturn("jwt");
        when(jwtService.getExpiracionSegundos()).thenReturn(1800L);
        when(empresaService.obtenerEntidad(3L)).thenReturn(Empresa.builder().id(3L).nombre("Empresa A").build());

        LoginResponse respuesta = authService.login(new LoginRequest(" ANA@a.com ", "clave"));

        assertThat(respuesta.token()).isEqualTo("jwt");
        assertThat(respuesta.tipo()).isEqualTo("Bearer");
        assertThat(respuesta.usuario().empresaNombre()).isEqualTo("Empresa A");
        verify(authenticationManager).authenticate(org.mockito.ArgumentMatchers.argThat(a ->
                a.getPrincipal().equals("ana@a.com")));
    }

    @Test
    void AuthService_login_CredencialesInvalidasPropagaLaExcepcionYNoEmiteToken() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("mal"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@a.com", "mala")))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwtService, never()).generarToken(any());
    }

    @Test
    void AuthService_yo_UsuarioDeOtraEmpresaEs404() {
        when(usuarioRepository.findByIdAndEmpresaId(7L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.yo(7L, 99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void AuthService_yo_DevuelveResumenConEmpresa() {
        when(usuarioRepository.findByIdAndEmpresaId(7L, 3L)).thenReturn(Optional.of(ana()));
        when(empresaService.obtenerEntidad(3L)).thenReturn(Empresa.builder().id(3L).nombre("Empresa A").build());

        assertThat(authService.yo(7L, 3L).correo()).isEqualTo("ana@a.com");
    }
}

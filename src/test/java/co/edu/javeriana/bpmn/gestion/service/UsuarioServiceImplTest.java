package co.edu.javeriana.bpmn.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.common.exception.ParametroInvalidoException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.gestion.dto.CambioEstadoRequest;
import co.edu.javeriana.bpmn.gestion.dto.CambioRolRequest;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioRequest;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioResponse;
import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;
import co.edu.javeriana.bpmn.gestion.repository.UsuarioRepository;
import co.edu.javeriana.bpmn.gestion.service.impl.UsuarioServiceImpl;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    private static final Long EMPRESA = 1L;
    private static final Long ACTOR = 10L;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioServiceImpl usuarioService;

    private static Usuario usuario(Long id, RolAcceso rol, boolean activo) {
        Usuario u = Usuario.builder().id(id).nombre("U" + id).correo("u" + id + "@a.com")
                .password("$2a$x").rol(rol).activo(activo).build();
        u.setEmpresaId(EMPRESA);
        return u;
    }

    @Test
    void UsuarioService_crear_NormalizaCorreoCifraClaveYAsignaEmpresa() {
        when(usuarioRepository.existsByCorreoIgnoreCase("eva@a.com")).thenReturn(false);
        when(passwordEncoder.encode("ClaveInicial1")).thenReturn("$2a$hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse creado = usuarioService.crear(
                new UsuarioRequest(" Eva ", " EVA@a.com ", RolAcceso.EDITOR, "ClaveInicial1"), EMPRESA);

        assertThat(creado.correo()).isEqualTo("eva@a.com");
        assertThat(creado.rol()).isEqualTo(RolAcceso.EDITOR);
        assertThat(creado.activo()).isTrue();
        verify(usuarioRepository).save(org.mockito.ArgumentMatchers.argThat(u ->
                u.getEmpresaId().equals(EMPRESA) && u.getPassword().equals("$2a$hash")));
    }

    @Test
    void UsuarioService_crear_CorreoExistenteLanzaReglaDeNegocio() {
        when(usuarioRepository.existsByCorreoIgnoreCase("eva@a.com")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.crear(
                new UsuarioRequest("Eva", "eva@a.com", RolAcceso.EDITOR, "ClaveInicial1"), EMPRESA))
                .isInstanceOf(ReglaDeNegocioException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void UsuarioService_obtener_DeOtraEmpresaEs404() {
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.obtener(5L, EMPRESA)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void UsuarioService_cambiarRol_SobreSiMismoLanzaReglaDeNegocio() {
        when(usuarioRepository.findByIdAndEmpresaId(ACTOR, EMPRESA))
                .thenReturn(Optional.of(usuario(ACTOR, RolAcceso.ADMINISTRADOR, true)));

        assertThatThrownBy(() -> usuarioService.cambiarRol(ACTOR, new CambioRolRequest(RolAcceso.LECTOR), EMPRESA, ACTOR))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("propio rol");
    }

    @Test
    void UsuarioService_cambiarRol_UltimoAdministradorNoSePuedeDegradar() {
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA))
                .thenReturn(Optional.of(usuario(5L, RolAcceso.ADMINISTRADOR, true)));
        when(usuarioRepository.countByEmpresaIdAndRolAndActivoTrue(EMPRESA, RolAcceso.ADMINISTRADOR)).thenReturn(1L);

        assertThatThrownBy(() -> usuarioService.cambiarRol(5L, new CambioRolRequest(RolAcceso.EDITOR), EMPRESA, ACTOR))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("al menos un administrador");
    }

    @Test
    void UsuarioService_cambiarRol_AdministradorConOtroActivoSiSeDegrada() {
        Usuario admin = usuario(5L, RolAcceso.ADMINISTRADOR, true);
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(admin));
        when(usuarioRepository.countByEmpresaIdAndRolAndActivoTrue(EMPRESA, RolAcceso.ADMINISTRADOR)).thenReturn(2L);

        assertThat(usuarioService.cambiarRol(5L, new CambioRolRequest(RolAcceso.EDITOR), EMPRESA, ACTOR).rol())
                .isEqualTo(RolAcceso.EDITOR);
    }

    @Test
    void UsuarioService_cambiarRol_EditorAAdministradorNoConsultaElConteo() {
        Usuario editor = usuario(5L, RolAcceso.EDITOR, true);
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(editor));

        assertThat(usuarioService.cambiarRol(5L, new CambioRolRequest(RolAcceso.ADMINISTRADOR), EMPRESA, ACTOR).rol())
                .isEqualTo(RolAcceso.ADMINISTRADOR);
        verify(usuarioRepository, never()).countByEmpresaIdAndRolAndActivoTrue(any(), any());
    }

    @Test
    void UsuarioService_cambiarEstado_SuPropiaCuentaLanzaReglaDeNegocio() {
        when(usuarioRepository.findByIdAndEmpresaId(ACTOR, EMPRESA))
                .thenReturn(Optional.of(usuario(ACTOR, RolAcceso.ADMINISTRADOR, true)));

        assertThatThrownBy(() -> usuarioService.cambiarEstado(ACTOR, new CambioEstadoRequest(false), EMPRESA, ACTOR))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void UsuarioService_cambiarEstado_EditorQuedaInactivoSinBorrarse() {
        Usuario editor = usuario(5L, RolAcceso.EDITOR, true);
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(editor));

        assertThat(usuarioService.cambiarEstado(5L, new CambioEstadoRequest(false), EMPRESA, ACTOR).activo()).isFalse();
        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    void UsuarioService_cambiarEstado_UltimoAdministradorActivoNo() {
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA))
                .thenReturn(Optional.of(usuario(5L, RolAcceso.ADMINISTRADOR, true)));
        when(usuarioRepository.countByEmpresaIdAndRolAndActivoTrue(EMPRESA, RolAcceso.ADMINISTRADOR)).thenReturn(1L);

        assertThatThrownBy(() -> usuarioService.cambiarEstado(5L, new CambioEstadoRequest(false), EMPRESA, ACTOR))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void UsuarioService_cambiarEstado_ReactivaUsuario() {
        Usuario inactivo = usuario(5L, RolAcceso.LECTOR, false);
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(inactivo));

        assertThat(usuarioService.cambiarEstado(5L, new CambioEstadoRequest(true), EMPRESA, ACTOR).activo()).isTrue();
    }

    @Test
    void UsuarioService_listar_OrdenNoPermitidoLanzaParametroInvalido() {
        PageRequest porClave = PageRequest.of(0, 10, Sort.by("password"));

        assertThatThrownBy(() -> usuarioService.listar(EMPRESA, null, porClave))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("password");
    }

    @Test
    void UsuarioService_listar_NombreNuloBuscaTodos() {
        PageRequest pagina = PageRequest.of(0, 10, Sort.by("nombre"));
        when(usuarioRepository.findByEmpresaIdAndNombreContainingIgnoreCase(eq(EMPRESA), eq(""), any()))
                .thenReturn(new PageImpl<>(List.of(usuario(5L, RolAcceso.EDITOR, true)), pagina, 1));

        assertThat(usuarioService.listar(EMPRESA, null, pagina).totalElements()).isEqualTo(1);
    }
}

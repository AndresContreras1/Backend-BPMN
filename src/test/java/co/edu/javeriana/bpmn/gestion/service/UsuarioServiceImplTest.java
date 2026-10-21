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

/**
 * Pruebas unitarias de UsuarioServiceImpl (crear usuarios, cambiar rol y estado, listar).
 * Con @ExtendWith(MockitoExtension.class) activa Mockito; @Mock crea dobles de las dependencias y @InjectMocks crea el servicio real con ellos.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    // EMPRESA: la empresa de trabajo. ACTOR: el administrador que ejecuta la acción.
    private static final Long EMPRESA = 1L;
    private static final Long ACTOR = 10L;

    // Dobles de repositorio y cifrador de claves.
    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    // Objeto bajo prueba.
    @InjectMocks
    private UsuarioServiceImpl usuarioService;

    // Auxiliar: construye un usuario de la empresa con el id, rol y estado indicados.
    private static Usuario usuario(Long id, RolAcceso rol, boolean activo) {
        Usuario u = Usuario.builder().id(id).nombre("U" + id).correo("u" + id + "@a.com")
                .password("$2a$x").rol(rol).activo(activo).build();
        u.setEmpresaId(EMPRESA);
        return u;
    }

    // Escenario: crear un usuario con nombre y correo con espacios y mayúsculas.
    // Esperado: correo en minúscula, clave cifrada, empresa asignada y activo.
    @Test
    void UsuarioService_crear_NormalizaCorreoCifraClaveYAsignaEmpresa() {
        // Preparar: el correo no existe; el cifrador devuelve un hash; save devuelve el mismo usuario.
        when(usuarioRepository.existsByCorreoIgnoreCase("eva@a.com")).thenReturn(false);
        when(passwordEncoder.encode("ClaveInicial1")).thenReturn("$2a$hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        // Actuar: crear.
        UsuarioResponse creado = usuarioService.crear(
                new UsuarioRequest(" Eva ", " EVA@a.com ", RolAcceso.EDITOR, "ClaveInicial1"), EMPRESA);

        // Comprobar la respuesta.
        assertThat(creado.correo()).isEqualTo("eva@a.com");
        assertThat(creado.rol()).isEqualTo(RolAcceso.EDITOR);
        assertThat(creado.activo()).isTrue();
        // Comprobar lo guardado con argThat: pertenece a la empresa y lleva la clave cifrada, no la original.
        verify(usuarioRepository).save(org.mockito.ArgumentMatchers.argThat(u ->
                u.getEmpresaId().equals(EMPRESA) && u.getPassword().equals("$2a$hash")));
    }

    // Escenario: el correo ya está en uso.
    // Esperado: ReglaDeNegocioException y no se guarda.
    @Test
    void UsuarioService_crear_CorreoExistenteLanzaReglaDeNegocio() {
        when(usuarioRepository.existsByCorreoIgnoreCase("eva@a.com")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.crear(
                new UsuarioRequest("Eva", "eva@a.com", RolAcceso.EDITOR, "ClaveInicial1"), EMPRESA))
                .isInstanceOf(ReglaDeNegocioException.class);
        verify(usuarioRepository, never()).save(any());
    }

    // Escenario: pedir un usuario que no es de la empresa.
    // Esperado: NotFoundException (404).
    @Test
    void UsuarioService_obtener_DeOtraEmpresaEs404() {
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.obtener(5L, EMPRESA)).isInstanceOf(NotFoundException.class);
    }

    // Escenario: el actor intenta cambiar su propio rol.
    // Esperado: ReglaDeNegocioException ("propio rol").
    @Test
    void UsuarioService_cambiarRol_SobreSiMismoLanzaReglaDeNegocio() {
        when(usuarioRepository.findByIdAndEmpresaId(ACTOR, EMPRESA))
                .thenReturn(Optional.of(usuario(ACTOR, RolAcceso.ADMINISTRADOR, true)));

        assertThatThrownBy(() -> usuarioService.cambiarRol(ACTOR, new CambioRolRequest(RolAcceso.LECTOR), EMPRESA, ACTOR))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("propio rol");
    }

    // Escenario: degradar al único administrador activo.
    // Esperado: rechazado; la empresa debe tener al menos un administrador.
    @Test
    void UsuarioService_cambiarRol_UltimoAdministradorNoSePuedeDegradar() {
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA))
                .thenReturn(Optional.of(usuario(5L, RolAcceso.ADMINISTRADOR, true)));
        // El conteo de administradores activos es 1.
        when(usuarioRepository.countByEmpresaIdAndRolAndActivoTrue(EMPRESA, RolAcceso.ADMINISTRADOR)).thenReturn(1L);

        assertThatThrownBy(() -> usuarioService.cambiarRol(5L, new CambioRolRequest(RolAcceso.EDITOR), EMPRESA, ACTOR))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("al menos un administrador");
    }

    // Escenario: degradar a un administrador cuando hay otro activo.
    // Esperado: permitido; el rol pasa a EDITOR.
    @Test
    void UsuarioService_cambiarRol_AdministradorConOtroActivoSiSeDegrada() {
        Usuario admin = usuario(5L, RolAcceso.ADMINISTRADOR, true);
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(admin));
        // Hay 2 administradores activos.
        when(usuarioRepository.countByEmpresaIdAndRolAndActivoTrue(EMPRESA, RolAcceso.ADMINISTRADOR)).thenReturn(2L);

        assertThat(usuarioService.cambiarRol(5L, new CambioRolRequest(RolAcceso.EDITOR), EMPRESA, ACTOR).rol())
                .isEqualTo(RolAcceso.EDITOR);
    }

    // Escenario: subir un editor a administrador.
    // Esperado: permitido y ni siquiera se cuenta administradores (no hace falta).
    @Test
    void UsuarioService_cambiarRol_EditorAAdministradorNoConsultaElConteo() {
        Usuario editor = usuario(5L, RolAcceso.EDITOR, true);
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(editor));

        assertThat(usuarioService.cambiarRol(5L, new CambioRolRequest(RolAcceso.ADMINISTRADOR), EMPRESA, ACTOR).rol())
                .isEqualTo(RolAcceso.ADMINISTRADOR);
        // Se verifica que no se consultó el conteo.
        verify(usuarioRepository, never()).countByEmpresaIdAndRolAndActivoTrue(any(), any());
    }

    // Escenario: el actor intenta desactivar su propia cuenta.
    // Esperado: ReglaDeNegocioException.
    @Test
    void UsuarioService_cambiarEstado_SuPropiaCuentaLanzaReglaDeNegocio() {
        when(usuarioRepository.findByIdAndEmpresaId(ACTOR, EMPRESA))
                .thenReturn(Optional.of(usuario(ACTOR, RolAcceso.ADMINISTRADOR, true)));

        assertThatThrownBy(() -> usuarioService.cambiarEstado(ACTOR, new CambioEstadoRequest(false), EMPRESA, ACTOR))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    // Escenario: desactivar a un editor.
    // Esperado: queda inactivo y no se borra el registro.
    @Test
    void UsuarioService_cambiarEstado_EditorQuedaInactivoSinBorrarse() {
        Usuario editor = usuario(5L, RolAcceso.EDITOR, true);
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(editor));

        assertThat(usuarioService.cambiarEstado(5L, new CambioEstadoRequest(false), EMPRESA, ACTOR).activo()).isFalse();
        verify(usuarioRepository, never()).delete(any());
    }

    // Escenario: desactivar al único administrador activo.
    // Esperado: ReglaDeNegocioException.
    @Test
    void UsuarioService_cambiarEstado_UltimoAdministradorActivoNo() {
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA))
                .thenReturn(Optional.of(usuario(5L, RolAcceso.ADMINISTRADOR, true)));
        when(usuarioRepository.countByEmpresaIdAndRolAndActivoTrue(EMPRESA, RolAcceso.ADMINISTRADOR)).thenReturn(1L);

        assertThatThrownBy(() -> usuarioService.cambiarEstado(5L, new CambioEstadoRequest(false), EMPRESA, ACTOR))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    // Escenario: activar un usuario inactivo.
    // Esperado: queda activo.
    @Test
    void UsuarioService_cambiarEstado_ReactivaUsuario() {
        Usuario inactivo = usuario(5L, RolAcceso.LECTOR, false);
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(inactivo));

        assertThat(usuarioService.cambiarEstado(5L, new CambioEstadoRequest(true), EMPRESA, ACTOR).activo()).isTrue();
    }

    // Escenario: listar ordenando por password.
    // Esperado: ParametroInvalidoException; no se puede ordenar por campos sensibles.
    @Test
    void UsuarioService_listar_OrdenNoPermitidoLanzaParametroInvalido() {
        PageRequest porClave = PageRequest.of(0, 10, Sort.by("password"));

        assertThatThrownBy(() -> usuarioService.listar(EMPRESA, null, porClave))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("password");
    }

    // Escenario: listar sin filtro de nombre.
    // Esperado: busca con texto vacío (todos) y devuelve el total 1.
    @Test
    void UsuarioService_listar_NombreNuloBuscaTodos() {
        PageRequest pagina = PageRequest.of(0, 10, Sort.by("nombre"));
        // any() acepta cualquier valor para el parámetro de paginación.
        when(usuarioRepository.findByEmpresaIdAndNombreContainingIgnoreCase(eq(EMPRESA), eq(""), any()))
                .thenReturn(new PageImpl<>(List.of(usuario(5L, RolAcceso.EDITOR, true)), pagina, 1));

        assertThat(usuarioService.listar(EMPRESA, null, pagina).totalElements()).isEqualTo(1);
    }
}

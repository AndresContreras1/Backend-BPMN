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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.gestion.dto.RegistroEmpresaRequest;
import co.edu.javeriana.bpmn.gestion.dto.RegistroEmpresaResponse;
import co.edu.javeriana.bpmn.gestion.entity.Empresa;
import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;
import co.edu.javeriana.bpmn.gestion.repository.EmpresaRepository;
import co.edu.javeriana.bpmn.gestion.repository.UsuarioRepository;
import co.edu.javeriana.bpmn.gestion.service.impl.EmpresaServiceImpl;

/**
 * Pruebas unitarias de EmpresaServiceImpl (registro y consulta de empresas) con Mockito.
 */
@ExtendWith(MockitoExtension.class)
class EmpresaServiceImplTest {

    // Dobles de repositorios y del cifrador de claves.
    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    // Objeto bajo prueba, con los mocks inyectados.
    @InjectMocks
    private EmpresaServiceImpl empresaService;

    // Solicitud de registro con espacios y mayúsculas a propósito para comprobar la normalización.
    private static RegistroEmpresaRequest request() {
        return new RegistroEmpresaRequest(" Facimus ", " 900123456-1 ", " Admin@Facimus.com ", "Laura", "ClaveSegura1");
    }

    // Escenario: registrar una empresa nueva.
    // Esperado: se crea el administrador de esa empresa con clave cifrada y correo normalizado.
    @Test
    void EmpresaService_registrar_CreaEmpresaYAdministradorConClaveCifrada() {
        // Preparar: ni el NIT ni el correo existen todavía.
        when(empresaRepository.existsByNitIgnoreCase("900123456-1")).thenReturn(false);
        when(usuarioRepository.existsByCorreoIgnoreCase("admin@facimus.com")).thenReturn(false);
        // thenAnswer: devuelve la misma empresa pero asignándole el id 5, como haría la base.
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(inv -> {
            Empresa e = inv.getArgument(0);
            e.setId(5L);
            return e;
        });
        // El cifrador falso devuelve un hash conocido.
        when(passwordEncoder.encode("ClaveSegura1")).thenReturn("$2a$hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        // Actuar: registrar.
        RegistroEmpresaResponse respuesta = empresaService.registrar(request());

        // Se captura el usuario guardado para revisar rol, empresa, clave cifrada y correo.
        ArgumentCaptor<Usuario> admin = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(admin.capture());
        assertThat(admin.getValue().getRol()).isEqualTo(RolAcceso.ADMINISTRADOR);
        assertThat(admin.getValue().getEmpresaId()).isEqualTo(5L);
        assertThat(admin.getValue().getPassword()).isEqualTo("$2a$hash");
        assertThat(admin.getValue().getCorreo()).isEqualTo("admin@facimus.com");
        // Comprobar: nombre y NIT sin espacios sobrantes.
        assertThat(respuesta.empresa().nombre()).isEqualTo("Facimus");
        assertThat(respuesta.empresa().nit()).isEqualTo("900123456-1");
    }

    // Escenario: el NIT ya existe.
    // Esperado: ReglaDeNegocioException que menciona el NIT y no se guarda nada.
    @Test
    void EmpresaService_registrar_NitDuplicadoLanzaReglaDeNegocio() {
        when(empresaRepository.existsByNitIgnoreCase("900123456-1")).thenReturn(true);

        assertThatThrownBy(() -> empresaService.registrar(request()))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("NIT");
        verify(empresaRepository, never()).save(any());
    }

    // Escenario: el NIT es nuevo pero el correo ya está en uso.
    // Esperado: ReglaDeNegocioException por el correo y nada se guarda.
    @Test
    void EmpresaService_registrar_CorreoYaUsadoLanzaReglaDeNegocio() {
        when(empresaRepository.existsByNitIgnoreCase("900123456-1")).thenReturn(false);
        when(usuarioRepository.existsByCorreoIgnoreCase("admin@facimus.com")).thenReturn(true);

        assertThatThrownBy(() -> empresaService.registrar(request()))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("correo");
        verify(empresaRepository, never()).save(any());
    }

    // Escenario: consultar los datos de una empresa distinta de la propia.
    // Esperado: NotFoundException sin consultar la base.
    @Test
    void EmpresaService_obtenerPropia_OtraEmpresaEs404SinConsultarLaBase() {
        assertThatThrownBy(() -> empresaService.obtenerPropia(9L, 5L)).isInstanceOf(NotFoundException.class);
        // Se verifica que ni siquiera se consultó el repositorio.
        verify(empresaRepository, never()).findByIdAndActivoTrue(any());
    }

    // Escenario: la empresa no existe o está inactiva.
    // Esperado: NotFoundException.
    @Test
    void EmpresaService_obtenerEntidad_InexistenteEs404() {
        when(empresaRepository.findByIdAndActivoTrue(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> empresaService.obtener(5L)).isInstanceOf(NotFoundException.class);
    }

    // Escenario: consultar la empresa propia.
    // Esperado: devuelve sus datos.
    @Test
    void EmpresaService_obtenerPropia_MismaEmpresaDevuelveDatos() {
        when(empresaRepository.findByIdAndActivoTrue(5L)).thenReturn(Optional.of(
                Empresa.builder().id(5L).nombre("Facimus").nit("1234567").correoContacto("a@a.com").build()));

        assertThat(empresaService.obtenerPropia(5L, 5L).nombre()).isEqualTo("Facimus");
    }
}

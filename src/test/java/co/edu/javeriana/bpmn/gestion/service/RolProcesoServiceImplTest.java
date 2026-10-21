package co.edu.javeriana.bpmn.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
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

import co.edu.javeriana.bpmn.common.dto.PageResponse;
import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoResumen;
import co.edu.javeriana.bpmn.gestion.dto.RolProcesoRequest;
import co.edu.javeriana.bpmn.gestion.dto.RolProcesoResponse;
import co.edu.javeriana.bpmn.gestion.dto.UsoDeRol;
import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.RolProceso;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;
import co.edu.javeriana.bpmn.gestion.repository.RolProcesoRepository;
import co.edu.javeriana.bpmn.gestion.service.impl.RolProcesoServiceImpl;

/**
 * Pruebas unitarias de RolProcesoServiceImpl (roles de proceso: crear, renombrar, eliminar y listar).
 * Con @ExtendWith(MockitoExtension.class) activa Mockito; @Mock crea dobles de las dependencias y @InjectMocks crea el servicio real con ellos.
 */
@ExtendWith(MockitoExtension.class)
class RolProcesoServiceImplTest {

    // Empresa con la que se ejecutan todos los casos.
    private static final Long EMPRESA = 1L;

    // Dobles de las dependencias: no hay base de datos.
    @Mock
    private RolProcesoRepository rolRepository;

    @Mock
    private HistorialService historialService;

    @Mock
    private ProcesoService procesoService;

    // Objeto bajo prueba.
    @InjectMocks
    private RolProcesoServiceImpl rolService;

    // Rol de ejemplo "Analista" de la empresa 1.
    private static RolProceso analista() {
        RolProceso r = RolProceso.builder().id(3L).nombre("Analista").build();
        r.setEmpresaId(EMPRESA);
        return r;
    }

    // Escenario: crear un rol con nombre que no existe (llega con espacios y descripción en blanco).
    // Esperado: se guarda normalizado, descripción nula, eliminable, y queda historial de la empresa.
    @Test
    void RolProcesoService_crear_NombreUnicoYRegistraHistorialDeEmpresa() {
        // Preparar: el nombre es libre; save asigna el id 3 al rol.
        when(rolRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrue("Analista", EMPRESA)).thenReturn(false);
        when(rolRepository.save(any(RolProceso.class))).thenAnswer(inv -> {
            RolProceso r = inv.getArgument(0);
            r.setId(3L);
            return r;
        });

        // Actuar: crear el rol.
        RolProcesoResponse creado = rolService.crear(new RolProcesoRequest(" Analista ", " "), EMPRESA);

        // Comprobar: nombre sin espacios, descripción en blanco convertida en null.
        assertThat(creado.nombre()).isEqualTo("Analista");
        assertThat(creado.descripcion()).isNull();
        assertThat(creado.eliminable()).isTrue();
        // Se verificó el registro en el historial; eq() compara valores exactos y anyString() acepta cualquier texto.
        verify(historialService).registrarDeEmpresa(eq(EMPRESA), eq(TipoCambio.CREACION),
                eq(ElementoHistorial.ROL_PROCESO), eq(3L), anyString());
    }

    // Escenario: ya existe un rol con ese nombre.
    // Esperado: ReglaDeNegocioException (409) y no se guarda.
    @Test
    void RolProcesoService_crear_DuplicadoLanza409() {
        when(rolRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrue("Analista", EMPRESA)).thenReturn(true);

        assertThatThrownBy(() -> rolService.crear(new RolProcesoRequest("Analista", null), EMPRESA))
                .isInstanceOf(ReglaDeNegocioException.class);
        verify(rolRepository, never()).save(any());
    }

    // Escenario: eliminar un rol usado por lanes de algún proceso.
    // Esperado: 409 y el detalle lista los procesos que lo usan.
    @Test
    void RolProcesoService_eliminar_EnUsoLanza409ConLosProcesos() {
        // Preparar: el rol existe, lo usan 2 lanes y pertenece al proceso Vacaciones.
        when(rolRepository.findByIdAndEmpresaIdAndActivoTrue(3L, EMPRESA)).thenReturn(Optional.of(analista()));
        when(rolRepository.contarLanesActivasQueLoUsan(3L)).thenReturn(2L);
        when(rolRepository.procesosQueLoUsan(3L)).thenReturn(List.of(new ProcesoResumen(5L, "Vacaciones")));

        // satisfies permite inspeccionar la excepción: el detalle debe ser exactamente [Vacaciones].
        assertThatThrownBy(() -> rolService.eliminar(3L, EMPRESA))
                .isInstanceOf(ReglaDeNegocioException.class)
                .satisfies(e -> assertThat(((ReglaDeNegocioException) e).getDetalles()).containsExactly("Vacaciones"));
    }

    // Escenario: eliminar un rol sin uso.
    // Esperado: borrado lógico (activo=false), nunca se elimina la fila.
    @Test
    void RolProcesoService_eliminar_SinUsoEsLogico() {
        RolProceso rol = analista();
        when(rolRepository.findByIdAndEmpresaIdAndActivoTrue(3L, EMPRESA)).thenReturn(Optional.of(rol));
        when(rolRepository.contarLanesActivasQueLoUsan(3L)).thenReturn(0L);

        // Actuar: eliminar.
        rolService.eliminar(3L, EMPRESA);

        assertThat(rol.isActivo()).isFalse();
        verify(rolRepository, never()).delete(any());
    }

    // Escenario: renombrar un rol usado en un proceso.
    // Esperado: el nombre cambia y se deja constancia en el historial de ese proceso.
    @Test
    void RolProcesoService_actualizar_RenombrarDejaConstanciaEnCadaProcesoQueLoUsa() {
        RolProceso rol = analista();
        // Preparar: el rol existe y lo usa el proceso Vacaciones.
        Proceso vacaciones = Proceso.builder().id(5L).build();
        when(rolRepository.findByIdAndEmpresaIdAndActivoTrue(3L, EMPRESA)).thenReturn(Optional.of(rol));
        when(rolRepository.procesosQueLoUsan(3L)).thenReturn(List.of(new ProcesoResumen(5L, "Vacaciones")));
        when(procesoService.obtenerDeEmpresa(5L, EMPRESA)).thenReturn(vacaciones);

        // Actuar: renombrar a "Analista senior".
        rolService.actualizar(3L, new RolProcesoRequest("Analista senior", null), EMPRESA);

        assertThat(rol.getNombre()).isEqualTo("Analista senior");
        // Comprobar: contains() acepta cualquier mensaje que incluya el nuevo nombre.
        verify(historialService).registrar(eq(vacaciones), eq(TipoCambio.EDICION), eq(ElementoHistorial.LANE), eq(null),
                contains("'Analista senior'"));
    }

    // Escenario: cambiar solo la descripción (mismo nombre).
    // Esperado: no se tocan los procesos ni su historial.
    @Test
    void RolProcesoService_actualizar_SoloDescripcionNoTocaLosProcesos() {
        when(rolRepository.findByIdAndEmpresaIdAndActivoTrue(3L, EMPRESA)).thenReturn(Optional.of(analista()));
        when(rolRepository.procesosQueLoUsan(3L)).thenReturn(List.of(new ProcesoResumen(5L, "Vacaciones")));

        rolService.actualizar(3L, new RolProcesoRequest("Analista", "Nueva descripción"), EMPRESA);

        // Comprobar: nunca se consultó ningún proceso.
        verify(procesoService, never()).obtenerDeEmpresa(any(), any());
    }

    // Escenario: listar roles de una página.
    // Esperado: el uso de todos los roles se consulta en una sola consulta y cada rol sale marcado en uso.
    @Test
    void RolProcesoService_listar_AgrupaElUsoEnUnaSolaConsulta() {
        RolProceso rol = analista();
        // Preparar: página 0 de tamaño 10; el repositorio devuelve un rol y el uso de ese rol por el proceso Vacaciones.
        PageRequest pagina = PageRequest.of(0, 10);
        when(rolRepository.findByEmpresaIdAndActivoTrueAndNombreContainingIgnoreCase(EMPRESA, "", conDesempate(pagina)))
                .thenReturn(new PageImpl<>(List.of(rol), pagina, 1));
        when(rolRepository.usoDeRoles(List.of(3L))).thenReturn(List.of(new UsoDeRol(3L, 5L, "Vacaciones")));

        // Actuar: listar sin filtro de nombre (null se trata como texto vacío).
        PageResponse<RolProcesoResponse> respuesta = rolService.listar(EMPRESA, null, pagina);

        // Comprobar: el rol está en uso y trae el proceso que lo usa.
        assertThat(respuesta.content().get(0).enUso()).isTrue();
        assertThat(respuesta.content().get(0).procesos()).extracting(ProcesoResumen::nombre).containsExactly("Vacaciones");
    }

    // Escenario: la búsqueda no devuelve roles.
    // Esperado: contenido vacío y no se consulta el uso (ahorra una consulta).
    @Test
    void RolProcesoService_listar_PaginaVaciaNoConsultaElUso() {
        PageRequest pagina = PageRequest.of(0, 10);
        when(rolRepository.findByEmpresaIdAndActivoTrueAndNombreContainingIgnoreCase(EMPRESA, "x", conDesempate(pagina)))
                .thenReturn(new PageImpl<>(List.of(), pagina, 0));

        assertThat(rolService.listar(EMPRESA, "x", pagina).content()).isEmpty();
        verify(rolRepository, never()).usoDeRoles(any());
    }

    // Escenario: pedir los procesos de un rol que no existe en la empresa.
    // Esperado: NotFoundException (404).
    @Test
    void RolProcesoService_obtenerDeEmpresa_DeOtraEmpresaEs404() {
        when(rolRepository.findByIdAndEmpresaIdAndActivoTrue(3L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rolService.procesos(3L, EMPRESA)).isInstanceOf(NotFoundException.class);
    }

    /** El servicio agrega el id como desempate del orden (Paginacion.validarOrden). */
    // Auxiliar: reproduce el orden con id añadido para que el mock coincida con lo que pide el servicio.
    private static PageRequest conDesempate(PageRequest pagina) {
        return PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), pagina.getSort().and(Sort.by("id")));
    }
}

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

@ExtendWith(MockitoExtension.class)
class RolProcesoServiceImplTest {

    private static final Long EMPRESA = 1L;

    @Mock
    private RolProcesoRepository rolRepository;

    @Mock
    private HistorialService historialService;

    @Mock
    private ProcesoService procesoService;

    @InjectMocks
    private RolProcesoServiceImpl rolService;

    private static RolProceso analista() {
        RolProceso r = RolProceso.builder().id(3L).nombre("Analista").build();
        r.setEmpresaId(EMPRESA);
        return r;
    }

    @Test
    void RolProcesoService_crear_NombreUnicoYRegistraHistorialDeEmpresa() {
        when(rolRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrue("Analista", EMPRESA)).thenReturn(false);
        when(rolRepository.save(any(RolProceso.class))).thenAnswer(inv -> {
            RolProceso r = inv.getArgument(0);
            r.setId(3L);
            return r;
        });

        RolProcesoResponse creado = rolService.crear(new RolProcesoRequest(" Analista ", " "), EMPRESA);

        assertThat(creado.nombre()).isEqualTo("Analista");
        assertThat(creado.descripcion()).isNull();
        assertThat(creado.eliminable()).isTrue();
        verify(historialService).registrarDeEmpresa(eq(EMPRESA), eq(TipoCambio.CREACION),
                eq(ElementoHistorial.ROL_PROCESO), eq(3L), anyString());
    }

    @Test
    void RolProcesoService_crear_DuplicadoLanza409() {
        when(rolRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrue("Analista", EMPRESA)).thenReturn(true);

        assertThatThrownBy(() -> rolService.crear(new RolProcesoRequest("Analista", null), EMPRESA))
                .isInstanceOf(ReglaDeNegocioException.class);
        verify(rolRepository, never()).save(any());
    }

    @Test
    void RolProcesoService_eliminar_EnUsoLanza409ConLosProcesos() {
        when(rolRepository.findByIdAndEmpresaIdAndActivoTrue(3L, EMPRESA)).thenReturn(Optional.of(analista()));
        when(rolRepository.contarLanesActivasQueLoUsan(3L)).thenReturn(2L);
        when(rolRepository.procesosQueLoUsan(3L)).thenReturn(List.of(new ProcesoResumen(5L, "Vacaciones")));

        assertThatThrownBy(() -> rolService.eliminar(3L, EMPRESA))
                .isInstanceOf(ReglaDeNegocioException.class)
                .satisfies(e -> assertThat(((ReglaDeNegocioException) e).getDetalles()).containsExactly("Vacaciones"));
    }

    @Test
    void RolProcesoService_eliminar_SinUsoEsLogico() {
        RolProceso rol = analista();
        when(rolRepository.findByIdAndEmpresaIdAndActivoTrue(3L, EMPRESA)).thenReturn(Optional.of(rol));
        when(rolRepository.contarLanesActivasQueLoUsan(3L)).thenReturn(0L);

        rolService.eliminar(3L, EMPRESA);

        assertThat(rol.isActivo()).isFalse();
        verify(rolRepository, never()).delete(any());
    }

    @Test
    void RolProcesoService_actualizar_RenombrarDejaConstanciaEnCadaProcesoQueLoUsa() {
        RolProceso rol = analista();
        Proceso vacaciones = Proceso.builder().id(5L).build();
        when(rolRepository.findByIdAndEmpresaIdAndActivoTrue(3L, EMPRESA)).thenReturn(Optional.of(rol));
        when(rolRepository.procesosQueLoUsan(3L)).thenReturn(List.of(new ProcesoResumen(5L, "Vacaciones")));
        when(procesoService.obtenerDeEmpresa(5L, EMPRESA)).thenReturn(vacaciones);

        rolService.actualizar(3L, new RolProcesoRequest("Analista senior", null), EMPRESA);

        assertThat(rol.getNombre()).isEqualTo("Analista senior");
        verify(historialService).registrar(eq(vacaciones), eq(TipoCambio.EDICION), eq(ElementoHistorial.LANE), eq(null),
                contains("'Analista senior'"));
    }

    @Test
    void RolProcesoService_actualizar_SoloDescripcionNoTocaLosProcesos() {
        when(rolRepository.findByIdAndEmpresaIdAndActivoTrue(3L, EMPRESA)).thenReturn(Optional.of(analista()));
        when(rolRepository.procesosQueLoUsan(3L)).thenReturn(List.of(new ProcesoResumen(5L, "Vacaciones")));

        rolService.actualizar(3L, new RolProcesoRequest("Analista", "Nueva descripción"), EMPRESA);

        verify(procesoService, never()).obtenerDeEmpresa(any(), any());
    }

    @Test
    void RolProcesoService_listar_AgrupaElUsoEnUnaSolaConsulta() {
        RolProceso rol = analista();
        PageRequest pagina = PageRequest.of(0, 10);
        when(rolRepository.findByEmpresaIdAndActivoTrueAndNombreContainingIgnoreCase(EMPRESA, "", pagina))
                .thenReturn(new PageImpl<>(List.of(rol), pagina, 1));
        when(rolRepository.usoDeRoles(List.of(3L))).thenReturn(List.of(new UsoDeRol(3L, 5L, "Vacaciones")));

        PageResponse<RolProcesoResponse> respuesta = rolService.listar(EMPRESA, null, pagina);

        assertThat(respuesta.content().get(0).enUso()).isTrue();
        assertThat(respuesta.content().get(0).procesos()).extracting(ProcesoResumen::nombre).containsExactly("Vacaciones");
    }

    @Test
    void RolProcesoService_listar_PaginaVaciaNoConsultaElUso() {
        PageRequest pagina = PageRequest.of(0, 10);
        when(rolRepository.findByEmpresaIdAndActivoTrueAndNombreContainingIgnoreCase(EMPRESA, "x", pagina))
                .thenReturn(new PageImpl<>(List.of(), pagina, 0));

        assertThat(rolService.listar(EMPRESA, "x", pagina).content()).isEmpty();
        verify(rolRepository, never()).usoDeRoles(any());
    }

    @Test
    void RolProcesoService_obtenerDeEmpresa_DeOtraEmpresaEs404() {
        when(rolRepository.findByIdAndEmpresaIdAndActivoTrue(3L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rolService.procesos(3L, EMPRESA)).isInstanceOf(NotFoundException.class);
    }
}

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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.common.exception.ParametroInvalidoException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.gestion.dto.FiltroProceso;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoActualizacionRequest;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoRequest;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoResponse;
import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.Empresa;
import co.edu.javeriana.bpmn.gestion.entity.EstadoProceso;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;
import co.edu.javeriana.bpmn.gestion.repository.ProcesoRepository;
import co.edu.javeriana.bpmn.gestion.service.impl.ProcesoServiceImpl;
import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoPool;
import co.edu.javeriana.bpmn.modelado.repository.PoolRepository;

@ExtendWith(MockitoExtension.class)
class ProcesoServiceImplTest {

    private static final Long EMPRESA = 1L;

    @Mock
    private ProcesoRepository procesoRepository;

    @Mock
    private PoolRepository poolRepository;

    @Mock
    private EmpresaService empresaService;

    @Mock
    private HistorialService historialService;

    @InjectMocks
    private ProcesoServiceImpl procesoService;

    private static Proceso vacaciones() {
        Proceso p = Proceso.builder().id(5L).nombre("Vacaciones").descripcion("Desc").categoria("TH")
                .estado(EstadoProceso.BORRADOR).version(0L).build();
        p.setEmpresaId(EMPRESA);
        return p;
    }

    @Test
    void ProcesoService_crear_NaceEnBorradorConPoolDeLaEmpresaEHistorial() {
        when(procesoRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrue("Vacaciones", EMPRESA)).thenReturn(false);
        when(procesoRepository.save(any(Proceso.class))).thenAnswer(inv -> {
            Proceso p = inv.getArgument(0);
            p.setId(5L);
            return p;
        });
        when(empresaService.obtenerEntidad(EMPRESA)).thenReturn(Empresa.builder().id(EMPRESA).nombre("Facimus").build());

        ProcesoResponse creado = procesoService.crear(new ProcesoRequest(" Vacaciones ", "  ", " TH "), EMPRESA);

        assertThat(creado.estado()).isEqualTo(EstadoProceso.BORRADOR);
        assertThat(creado.nombre()).isEqualTo("Vacaciones");
        assertThat(creado.descripcion()).isNull();
        assertThat(creado.propio()).isTrue();
        ArgumentCaptor<Pool> pool = ArgumentCaptor.forClass(Pool.class);
        verify(poolRepository).save(pool.capture());
        assertThat(pool.getValue().getTipo()).isEqualTo(TipoPool.PROPIETARIO);
        assertThat(pool.getValue().getNombre()).isEqualTo("Facimus");
        assertThat(pool.getValue().getEmpresaId()).isEqualTo(EMPRESA);
        assertThat(pool.getValue().isCajaNegra()).isFalse();
        verify(historialService).registrar(any(Proceso.class), eq(TipoCambio.CREACION), eq(ElementoHistorial.PROCESO),
                eq(5L), anyString());
    }

    @Test
    void ProcesoService_crear_NombreRepetidoEnLaEmpresaLanza409SinGuardar() {
        when(procesoRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrue("Vacaciones", EMPRESA)).thenReturn(true);

        assertThatThrownBy(() -> procesoService.crear(new ProcesoRequest("Vacaciones", null, "TH"), EMPRESA))
                .isInstanceOf(ReglaDeNegocioException.class);
        verify(procesoRepository, never()).save(any());
        verify(poolRepository, never()).save(any());
    }

    @Test
    void ProcesoService_actualizar_RegistraLosCambiosConValorAnteriorYNuevo() {
        Proceso proceso = vacaciones();
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, EMPRESA)).thenReturn(Optional.of(proceso));

        procesoService.actualizar(5L, new ProcesoActualizacionRequest("Vacaciones 2026", "Desc", "TH",
                EstadoProceso.BORRADOR, null), EMPRESA);

        verify(historialService).registrar(eq(proceso), eq(TipoCambio.EDICION), eq(ElementoHistorial.PROCESO), eq(5L),
                contains("nombre: 'Vacaciones' → 'Vacaciones 2026'"));
        assertThat(proceso.getNombre()).isEqualTo("Vacaciones 2026");
    }

    @Test
    void ProcesoService_actualizar_CambioDeEstadoSeRegistraComoCambioDeEstado() {
        Proceso proceso = vacaciones();
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, EMPRESA)).thenReturn(Optional.of(proceso));

        procesoService.actualizar(5L, new ProcesoActualizacionRequest("Vacaciones", "Desc", "TH",
                EstadoProceso.PUBLICADO, 0L), EMPRESA);

        verify(historialService).registrar(eq(proceso), eq(TipoCambio.CAMBIO_ESTADO), eq(ElementoHistorial.PROCESO),
                eq(5L), contains("estado: 'BORRADOR' → 'PUBLICADO'"));
    }

    @Test
    void ProcesoService_actualizar_SinCambiosNoEscribeHistorial() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, EMPRESA)).thenReturn(Optional.of(vacaciones()));

        procesoService.actualizar(5L, new ProcesoActualizacionRequest("Vacaciones", "Desc", "TH",
                EstadoProceso.BORRADOR, null), EMPRESA);

        verify(historialService, never()).registrar(any(), any(), any(), any(), anyString());
    }

    @Test
    void ProcesoService_actualizar_VersionDesactualizadaLanzaConflicto() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, EMPRESA)).thenReturn(Optional.of(vacaciones()));

        assertThatThrownBy(() -> procesoService.actualizar(5L, new ProcesoActualizacionRequest("V", null, "TH",
                EstadoProceso.BORRADOR, 7L), EMPRESA))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void ProcesoService_actualizar_NombreDeOtroProcesoActivoLanza409() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, EMPRESA)).thenReturn(Optional.of(vacaciones()));
        when(procesoRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrueAndIdNot("Compras", EMPRESA, 5L))
                .thenReturn(true);

        assertThatThrownBy(() -> procesoService.actualizar(5L, new ProcesoActualizacionRequest("Compras", null, "TH",
                EstadoProceso.BORRADOR, null), EMPRESA))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void ProcesoService_eliminar_EsLogicoYQuedaEnElHistorial() {
        Proceso proceso = vacaciones();
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, EMPRESA)).thenReturn(Optional.of(proceso));

        procesoService.eliminar(5L, EMPRESA);

        assertThat(proceso.isActivo()).isFalse();
        verify(procesoRepository, never()).delete(any());
        verify(historialService).registrar(eq(proceso), eq(TipoCambio.ELIMINACION), eq(ElementoHistorial.PROCESO),
                eq(5L), anyString());
    }

    @Test
    void ProcesoService_eliminar_DeOtraEmpresaOYaEliminadoEs404() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> procesoService.eliminar(5L, EMPRESA)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void ProcesoService_obtener_InactivoSePuedeConsultar() {
        Proceso inactivo = vacaciones();
        inactivo.setActivo(false);
        when(procesoRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(inactivo));

        assertThat(procesoService.obtener(5L, EMPRESA).activo()).isFalse();
    }

    @Test
    void ProcesoService_listar_NormalizaFiltrosVaciosYValidaElOrden() {
        PageRequest pagina = PageRequest.of(0, 10, Sort.by("nombre"));
        when(procesoRepository.buscar(EMPRESA, "", null, "", true, pagina))
                .thenReturn(new PageImpl<>(List.of(vacaciones()), pagina, 1));

        assertThat(procesoService.listar(EMPRESA, new FiltroProceso(null, null, null, true), pagina).totalElements())
                .isEqualTo(1);

        assertThatThrownBy(() -> procesoService.listar(EMPRESA, new FiltroProceso(null, null, null, true),
                PageRequest.of(0, 10, Sort.by("empresaId")))).isInstanceOf(ParametroInvalidoException.class);
    }

    @Test
    void ProcesoService_historial_OrdenNoPermitidoLanza400() {
        assertThatThrownBy(() -> procesoService.historial(5L, EMPRESA, PageRequest.of(0, 10, Sort.by("usuarioId"))))
                .isInstanceOf(ParametroInvalidoException.class);
    }
}

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
import co.edu.javeriana.bpmn.gestion.dto.ResumenProcesosResponse;
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
import co.edu.javeriana.bpmn.modelado.service.ValidadorDeProcesoService;

/**
 * Pruebas unitarias de ProcesoServiceImpl (crear, actualizar, publicar, eliminar, listar y resumen de procesos).
 * Con @ExtendWith(MockitoExtension.class) activa Mockito; @Mock crea dobles de las dependencias y @InjectMocks crea el servicio real con ellos.
 */
@ExtendWith(MockitoExtension.class)
class ProcesoServiceImplTest {

    // Empresa con la que se ejecutan los casos.
    private static final Long EMPRESA = 1L;

    // Dobles de las dependencias (repositorios, empresa, historial y validador del diagrama).
    @Mock
    private ProcesoRepository procesoRepository;

    @Mock
    private PoolRepository poolRepository;

    @Mock
    private EmpresaService empresaService;

    @Mock
    private HistorialService historialService;

    @Mock
    private ValidadorDeProcesoService validador;

    // Objeto bajo prueba.
    @InjectMocks
    private ProcesoServiceImpl procesoService;

    // Auxiliar: proceso "Vacaciones" en BORRADOR, versión 0, de la empresa 1.
    private static Proceso vacaciones() {
        Proceso p = Proceso.builder().id(5L).nombre("Vacaciones").descripcion("Desc").categoria("TH")
                .estado(EstadoProceso.BORRADOR).version(0L).build();
        p.setEmpresaId(EMPRESA);
        return p;
    }

    // Escenario: crear un proceso (nombre y categoría con espacios, descripción en blanco).
    // Esperado: nace en BORRADOR, se crea su pool propietario con el nombre de la empresa y se registra en el historial.
    @Test
    void ProcesoService_crear_NaceEnBorradorConPoolDeLaEmpresaEHistorial() {
        // Preparar: nombre libre; save asigna el id 5; la empresa se llama Facimus.
        when(procesoRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrue("Vacaciones", EMPRESA)).thenReturn(false);
        when(procesoRepository.save(any(Proceso.class))).thenAnswer(inv -> {
            Proceso p = inv.getArgument(0);
            p.setId(5L);
            return p;
        });
        when(empresaService.obtenerEntidad(EMPRESA)).thenReturn(Empresa.builder().id(EMPRESA).nombre("Facimus").build());

        // Actuar: crear el proceso.
        ProcesoResponse creado = procesoService.crear(new ProcesoRequest(" Vacaciones ", "  ", " TH "), EMPRESA);

        // Comprobar la respuesta: normalizada y marcada como propio.
        assertThat(creado.estado()).isEqualTo(EstadoProceso.BORRADOR);
        assertThat(creado.nombre()).isEqualTo("Vacaciones");
        assertThat(creado.descripcion()).isNull();
        assertThat(creado.propio()).isTrue();
        // Se captura el pool guardado para revisar tipo, nombre, empresa y que no sea caja negra.
        ArgumentCaptor<Pool> pool = ArgumentCaptor.forClass(Pool.class);
        verify(poolRepository).save(pool.capture());
        assertThat(pool.getValue().getTipo()).isEqualTo(TipoPool.PROPIETARIO);
        assertThat(pool.getValue().getNombre()).isEqualTo("Facimus");
        assertThat(pool.getValue().getEmpresaId()).isEqualTo(EMPRESA);
        assertThat(pool.getValue().isCajaNegra()).isFalse();
        // Comprobar: se registró la creación en el historial.
        verify(historialService).registrar(any(Proceso.class), eq(TipoCambio.CREACION), eq(ElementoHistorial.PROCESO),
                eq(5L), anyString());
    }

    // Escenario: ya existe un proceso activo con ese nombre.
    // Esperado: ReglaDeNegocioException (409) y no se guarda proceso ni pool.
    @Test
    void ProcesoService_crear_NombreRepetidoEnLaEmpresaLanza409SinGuardar() {
        when(procesoRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrue("Vacaciones", EMPRESA)).thenReturn(true);

        assertThatThrownBy(() -> procesoService.crear(new ProcesoRequest("Vacaciones", null, "TH"), EMPRESA))
                .isInstanceOf(ReglaDeNegocioException.class);
        verify(procesoRepository, never()).save(any());
        verify(poolRepository, never()).save(any());
    }

    // Escenario: renombrar un proceso.
    // Esperado: el historial incluye el valor anterior y el nuevo.
    @Test
    void ProcesoService_actualizar_RegistraLosCambiosConValorAnteriorYNuevo() {
        // Preparar: el proceso existe en la empresa.
        Proceso proceso = vacaciones();
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, EMPRESA)).thenReturn(Optional.of(proceso));

        // Actuar: cambiar el nombre.
        procesoService.actualizar(5L, new ProcesoActualizacionRequest("Vacaciones 2026", "Desc", "TH",
                EstadoProceso.BORRADOR, null), EMPRESA);

        // Comprobar: el texto del cambio contiene nombre: 'antes' → 'después'.
        verify(historialService).registrar(eq(proceso), eq(TipoCambio.EDICION), eq(ElementoHistorial.PROCESO), eq(5L),
                contains("nombre: 'Vacaciones' → 'Vacaciones 2026'"));
        assertThat(proceso.getNombre()).isEqualTo("Vacaciones 2026");
    }

    // Escenario: pasar de BORRADOR a PUBLICADO.
    // Esperado: se registra como cambio de estado y se valida la coherencia del diagrama.
    @Test
    void ProcesoService_actualizar_CambioDeEstadoSeRegistraComoCambioDeEstado() {
        Proceso proceso = vacaciones();
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, EMPRESA)).thenReturn(Optional.of(proceso));

        procesoService.actualizar(5L, new ProcesoActualizacionRequest("Vacaciones", "Desc", "TH",
                EstadoProceso.PUBLICADO, 0L), EMPRESA);

        // Comprobar: tipo de cambio CAMBIO_ESTADO con el detalle del estado.
        verify(historialService).registrar(eq(proceso), eq(TipoCambio.CAMBIO_ESTADO), eq(ElementoHistorial.PROCESO),
                eq(5L), contains("estado: 'BORRADOR' → 'PUBLICADO'"));
        // Comprobar: se llamó al validador antes de publicar.
        verify(validador).exigirCoherenciaParaPublicar(proceso);
    }

    // Escenario: publicar un proceso cuyo diagrama no es coherente.
    // Esperado: la excepción del validador se propaga y no se registra nada en el historial.
    @Test
    void ProcesoService_publicar_DiagramaIncoherenteNoSePublicaNiSeRegistra() {
        Proceso proceso = vacaciones();
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, EMPRESA)).thenReturn(Optional.of(proceso));
        // doThrow...when: configura que el validador lance la excepción (forma usada con métodos void).
        org.mockito.Mockito.doThrow(new ReglaDeNegocioException("No se puede publicar", List.of("Sin inicio")))
                .when(validador).exigirCoherenciaParaPublicar(proceso);

        assertThatThrownBy(() -> procesoService.actualizar(5L, new ProcesoActualizacionRequest("Vacaciones", "Desc", "TH",
                EstadoProceso.PUBLICADO, null), EMPRESA)).isInstanceOf(ReglaDeNegocioException.class);
        verify(historialService, never()).registrar(any(), any(), any(), any(), anyString());
    }

    // Escenario: volver de PUBLICADO a BORRADOR.
    // Esperado: no se valida el diagrama.
    @Test
    void ProcesoService_volverABorrador_NoValidaElDiagrama() {
        Proceso proceso = vacaciones();
        proceso.setEstado(EstadoProceso.PUBLICADO);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, EMPRESA)).thenReturn(Optional.of(proceso));

        procesoService.actualizar(5L, new ProcesoActualizacionRequest("Vacaciones", "Desc", "TH",
                EstadoProceso.BORRADOR, null), EMPRESA);

        verify(validador, never()).exigirCoherenciaParaPublicar(any());
    }

    // Escenario: actualizar con los mismos valores.
    // Esperado: no se escribe nada en el historial.
    @Test
    void ProcesoService_actualizar_SinCambiosNoEscribeHistorial() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, EMPRESA)).thenReturn(Optional.of(vacaciones()));

        procesoService.actualizar(5L, new ProcesoActualizacionRequest("Vacaciones", "Desc", "TH",
                EstadoProceso.BORRADOR, null), EMPRESA);

        verify(historialService, never()).registrar(any(), any(), any(), any(), anyString());
    }

    // Escenario: el cliente envía una versión (7) distinta de la actual (0).
    // Esperado: ObjectOptimisticLockingFailureException (bloqueo optimista, otro usuario ya modificó).
    @Test
    void ProcesoService_actualizar_VersionDesactualizadaLanzaConflicto() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, EMPRESA)).thenReturn(Optional.of(vacaciones()));

        assertThatThrownBy(() -> procesoService.actualizar(5L, new ProcesoActualizacionRequest("V", null, "TH",
                EstadoProceso.BORRADOR, 7L), EMPRESA))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // Escenario: renombrar con un nombre que ya usa otro proceso activo.
    // Esperado: ReglaDeNegocioException (409).
    @Test
    void ProcesoService_actualizar_NombreDeOtroProcesoActivoLanza409() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, EMPRESA)).thenReturn(Optional.of(vacaciones()));
        when(procesoRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrueAndIdNot("Compras", EMPRESA, 5L))
                .thenReturn(true);

        assertThatThrownBy(() -> procesoService.actualizar(5L, new ProcesoActualizacionRequest("Compras", null, "TH",
                EstadoProceso.BORRADOR, null), EMPRESA))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    // Escenario: eliminar un proceso.
    // Esperado: borrado lógico (activo=false), sin delete físico, y queda en el historial.
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

    // Escenario: eliminar un proceso inexistente, de otra empresa o ya eliminado.
    // Esperado: NotFoundException (404).
    @Test
    void ProcesoService_eliminar_DeOtraEmpresaOYaEliminadoEs404() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> procesoService.eliminar(5L, EMPRESA)).isInstanceOf(NotFoundException.class);
    }

    // Escenario: consultar un proceso eliminado.
    // Esperado: se puede leer y viene con activo=false.
    @Test
    void ProcesoService_obtener_InactivoSePuedeConsultar() {
        Proceso inactivo = vacaciones();
        inactivo.setActivo(false);
        when(procesoRepository.findLegibleByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(inactivo));

        assertThat(procesoService.obtener(5L, EMPRESA).activo()).isFalse();
    }

    // Escenario: listar sin filtros y con un orden no permitido.
    // Esperado: los filtros nulos se tratan como vacíos, el orden lleva id de desempate y un campo no permitido lanza ParametroInvalidoException.
    @Test
    void ProcesoService_listar_NormalizaFiltrosVaciosValidaElOrdenYDesempataPorId() {
        PageRequest pagina = PageRequest.of(0, 10, Sort.by("nombre"));
        // El repositorio solo responde si recibe exactamente el orden con id añadido.
        PageRequest conDesempate = PageRequest.of(0, 10, Sort.by("nombre").and(Sort.by("id")));
        when(procesoRepository.buscar(EMPRESA, "", null, "", true, conDesempate))
                .thenReturn(new PageImpl<>(List.of(vacaciones()), pagina, 1));

        assertThat(procesoService.listar(EMPRESA, new FiltroProceso(null, null, null, true), pagina).totalElements())
                .isEqualTo(1);

        // Orden por empresaId (no permitido): debe rechazarse.
        assertThatThrownBy(() -> procesoService.listar(EMPRESA, new FiltroProceso(null, null, null, true),
                PageRequest.of(0, 10, Sort.by("empresaId")))).isInstanceOf(ParametroInvalidoException.class);
    }

    // Escenario: calcular indicadores del inicio.
    // Esperado: total = borradores + publicados (5), más eliminados y compartidos.
    @Test
    void ProcesoService_resumen_SumaLosActivosPorEstadoYAgregaEliminadosYCompartidos() {
        when(procesoRepository.contarPorEstado(EMPRESA, EstadoProceso.BORRADOR)).thenReturn(3L);
        when(procesoRepository.contarPorEstado(EMPRESA, EstadoProceso.PUBLICADO)).thenReturn(2L);
        when(procesoRepository.countByEmpresaIdAndActivoFalse(EMPRESA)).thenReturn(1L);
        when(procesoRepository.contarCompartidosCon(EMPRESA)).thenReturn(4L);

        assertThat(procesoService.resumen(EMPRESA)).isEqualTo(new ResumenProcesosResponse(5, 3, 2, 1, 4));
    }

    // Escenario: pedir el historial ordenado por un campo no permitido.
    // Esperado: ParametroInvalidoException (400).
    @Test
    void ProcesoService_historial_OrdenNoPermitidoLanza400() {
        assertThatThrownBy(() -> procesoService.historial(5L, EMPRESA, PageRequest.of(0, 10, Sort.by("usuarioId"))))
                .isInstanceOf(ParametroInvalidoException.class);
    }
}

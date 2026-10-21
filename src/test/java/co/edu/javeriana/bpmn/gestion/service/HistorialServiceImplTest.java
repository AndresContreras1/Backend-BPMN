package co.edu.javeriana.bpmn.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
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

import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.HistorialCambio;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;
import co.edu.javeriana.bpmn.gestion.repository.HistorialCambioRepository;
import co.edu.javeriana.bpmn.gestion.service.impl.HistorialServiceImpl;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import co.edu.javeriana.bpmn.security.ContextoDeSeguridad;

/**
 * Pruebas unitarias de HistorialServiceImpl (registro del historial de cambios) con Mockito.
 * Con @ExtendWith activa Mockito; @Mock crea dobles de las dependencias; @InjectMocks crea el servicio real inyectándoles esos dobles.
 */
@ExtendWith(MockitoExtension.class)
class HistorialServiceImplTest {

    // Doble del repositorio: no toca base de datos; se verifica qué se le pidió guardar.
    @Mock
    private HistorialCambioRepository historialRepository;

    @Mock
    private ContextoDeSeguridad contextoDeSeguridad;

    // Objeto bajo prueba (real, con los mocks inyectados).
    @InjectMocks
    private HistorialServiceImpl historialService;

    // Proceso de ejemplo de la empresa 1.
    private static Proceso proceso() {
        Proceso p = Proceso.builder().id(5L).nombre("Vacaciones").categoria("TH").build();
        p.setEmpresaId(1L);
        return p;
    }

    // Escenario: registrar un cambio con un usuario autenticado.
    // Esperado: se guarda autor, fecha, empresa y advertencias unidas por salto de línea.
    @Test
    void HistorialService_registrar_GuardaAutorFechaYAdvertencias() {
        // when/thenReturn: el mock responde que el usuario actual es Ana (editora de la empresa 1).
        when(contextoDeSeguridad.actual())
                .thenReturn(Optional.of(new ApiPrincipal(10L, 1L, "ana@a.com", RolAcceso.EDITOR)));

        // Actuar: registrar la eliminación de una actividad con dos advertencias.
        historialService.registrar(proceso(), TipoCambio.ELIMINACION, ElementoHistorial.ACTIVIDAD, 9L,
                "Actividad eliminada", List.of("Revisar quedó sin salida", "Fin quedó inalcanzable"));

        // ArgumentCaptor captura el objeto que se pasó a save para inspeccionarlo; verify comprueba que save se llamó una vez.
        ArgumentCaptor<HistorialCambio> cambio = ArgumentCaptor.forClass(HistorialCambio.class);
        verify(historialRepository).save(cambio.capture());
        assertThat(cambio.getValue().getUsuarioCorreo()).isEqualTo("ana@a.com");
        assertThat(cambio.getValue().getUsuarioId()).isEqualTo(10L);
        assertThat(cambio.getValue().getFecha()).isNotNull();
        assertThat(cambio.getValue().getEmpresaId()).isEqualTo(1L);
        assertThat(cambio.getValue().getAdvertencias()).isEqualTo("Revisar quedó sin salida\nFin quedó inalcanzable");
    }

    // Escenario: no hay usuario autenticado y el detalle mide 1500 caracteres.
    // Esperado: autor "sistema", sin proceso y detalle recortado a 1000.
    @Test
    void HistorialService_registrarSinUsuario_AutorEsSistemaYDetalleRecortado() {
        // El mock indica que no hay usuario en sesión.
        when(contextoDeSeguridad.actual()).thenReturn(Optional.empty());

        historialService.registrarDeEmpresa(1L, TipoCambio.EDICION, ElementoHistorial.ROL_PROCESO, 3L, "x".repeat(1500));

        ArgumentCaptor<HistorialCambio> cambio = ArgumentCaptor.forClass(HistorialCambio.class);
        verify(historialRepository).save(cambio.capture());
        assertThat(cambio.getValue().getUsuarioCorreo()).isEqualTo("sistema");
        assertThat(cambio.getValue().getProceso()).isNull();
        assertThat(cambio.getValue().getDetalle()).hasSize(1000);
    }
}

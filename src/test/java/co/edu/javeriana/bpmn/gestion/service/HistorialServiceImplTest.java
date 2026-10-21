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

@ExtendWith(MockitoExtension.class)
class HistorialServiceImplTest {

    @Mock
    private HistorialCambioRepository historialRepository;

    @Mock
    private ContextoDeSeguridad contextoDeSeguridad;

    @InjectMocks
    private HistorialServiceImpl historialService;

    private static Proceso proceso() {
        Proceso p = Proceso.builder().id(5L).nombre("Vacaciones").categoria("TH").build();
        p.setEmpresaId(1L);
        return p;
    }

    @Test
    void HistorialService_registrar_GuardaAutorFechaYAdvertencias() {
        when(contextoDeSeguridad.actual())
                .thenReturn(Optional.of(new ApiPrincipal(10L, 1L, "ana@a.com", RolAcceso.EDITOR)));

        historialService.registrar(proceso(), TipoCambio.ELIMINACION, ElementoHistorial.ACTIVIDAD, 9L,
                "Actividad eliminada", List.of("Revisar quedó sin salida", "Fin quedó inalcanzable"));

        ArgumentCaptor<HistorialCambio> cambio = ArgumentCaptor.forClass(HistorialCambio.class);
        verify(historialRepository).save(cambio.capture());
        assertThat(cambio.getValue().getUsuarioCorreo()).isEqualTo("ana@a.com");
        assertThat(cambio.getValue().getUsuarioId()).isEqualTo(10L);
        assertThat(cambio.getValue().getFecha()).isNotNull();
        assertThat(cambio.getValue().getEmpresaId()).isEqualTo(1L);
        assertThat(cambio.getValue().getAdvertencias()).isEqualTo("Revisar quedó sin salida\nFin quedó inalcanzable");
    }

    @Test
    void HistorialService_registrarSinUsuario_AutorEsSistemaYDetalleRecortado() {
        when(contextoDeSeguridad.actual()).thenReturn(Optional.empty());

        historialService.registrarDeEmpresa(1L, TipoCambio.EDICION, ElementoHistorial.ROL_PROCESO, 3L, "x".repeat(1500));

        ArgumentCaptor<HistorialCambio> cambio = ArgumentCaptor.forClass(HistorialCambio.class);
        verify(historialRepository).save(cambio.capture());
        assertThat(cambio.getValue().getUsuarioCorreo()).isEqualTo("sistema");
        assertThat(cambio.getValue().getProceso()).isNull();
        assertThat(cambio.getValue().getDetalle()).hasSize(1000);
    }
}

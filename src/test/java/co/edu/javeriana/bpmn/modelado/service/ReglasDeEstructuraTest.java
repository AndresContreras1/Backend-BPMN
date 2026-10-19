package co.edu.javeriana.bpmn.modelado.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.modelado.entity.PermisoEstructura;
import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoPool;
import co.edu.javeriana.bpmn.modelado.repository.PoolRepository;
import co.edu.javeriana.bpmn.modelado.service.impl.ReglasDeEstructura;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import co.edu.javeriana.bpmn.security.ContextoDeSeguridad;

@ExtendWith(MockitoExtension.class)
class ReglasDeEstructuraTest {

    @Mock
    private PoolRepository poolRepository;

    @Mock
    private ContextoDeSeguridad contextoDeSeguridad;

    @InjectMocks
    private ReglasDeEstructura reglas;

    private final Proceso proceso = Proceso.builder().id(5L).build();

    private void como(RolAcceso rol) {
        when(contextoDeSeguridad.actual()).thenReturn(Optional.of(new ApiPrincipal(1L, 1L, "u@a.com", rol)));
    }

    private void conPermiso(PermisoEstructura permiso) {
        when(poolRepository.findFirstByProcesoIdAndTipoAndActivoTrue(5L, TipoPool.PROPIETARIO))
                .thenReturn(Optional.of(Pool.builder().permisoEstructura(permiso).build()));
    }

    @Test
    void ReglasDeEstructura_administrador_SiemprePuede() {
        como(RolAcceso.ADMINISTRADOR);

        assertThatCode(() -> reglas.verificarPuedeModificar(proceso)).doesNotThrowAnyException();
    }

    @Test
    void ReglasDeEstructura_lector_NuncaPuede() {
        como(RolAcceso.LECTOR);
        conPermiso(PermisoEstructura.ADMINISTRADOR_Y_EDITOR);

        assertThatThrownBy(() -> reglas.verificarPuedeModificar(proceso)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ReglasDeEstructura_editor_SegunElPermisoDelPoolPropietario() {
        como(RolAcceso.EDITOR);
        conPermiso(PermisoEstructura.ADMINISTRADOR_Y_EDITOR);
        assertThatCode(() -> reglas.verificarPuedeModificar(proceso)).doesNotThrowAnyException();

        conPermiso(PermisoEstructura.SOLO_ADMINISTRADOR);
        assertThatThrownBy(() -> reglas.verificarPuedeModificar(proceso)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ReglasDeEstructura_sinUsuarioAutenticado_Rechaza() {
        when(contextoDeSeguridad.actual()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reglas.verificarPuedeModificar(proceso)).isInstanceOf(AccessDeniedException.class);
    }
}

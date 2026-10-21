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

/**
 * Pruebas unitarias de ReglasDeEstructura: quién puede modificar la estructura de un proceso
 * según su rol y el permiso del pool propietario. Usa Mockito.
 */
@ExtendWith(MockitoExtension.class)
class ReglasDeEstructuraTest {

    // Dobles del repositorio de pools y del contexto de seguridad.
    @Mock
    private PoolRepository poolRepository;

    @Mock
    private ContextoDeSeguridad contextoDeSeguridad;

    // Objeto bajo prueba.
    @InjectMocks
    private ReglasDeEstructura reglas;

    // Proceso de ejemplo (id 5).
    private final Proceso proceso = Proceso.builder().id(5L).build();

    // Auxiliar: configura el mock para que el usuario actual tenga ese rol.
    private void como(RolAcceso rol) {
        when(contextoDeSeguridad.actual()).thenReturn(Optional.of(new ApiPrincipal(1L, 1L, "u@a.com", rol)));
    }

    // Auxiliar: configura el mock para que el pool propietario tenga ese permiso de estructura.
    private void conPermiso(PermisoEstructura permiso) {
        when(poolRepository.findFirstByProcesoIdAndTipoAndActivoTrue(5L, TipoPool.PROPIETARIO))
                .thenReturn(Optional.of(Pool.builder().permisoEstructura(permiso).build()));
    }

    // Escenario: un administrador intenta modificar.
    // Esperado: permitido siempre (no lanza excepción).
    @Test
    void ReglasDeEstructura_administrador_SiemprePuede() {
        como(RolAcceso.ADMINISTRADOR);

        assertThatCode(() -> reglas.verificarPuedeModificar(proceso)).doesNotThrowAnyException();
    }

    // Escenario: un lector intenta modificar, aunque el pool permita editores.
    // Esperado: AccessDeniedException.
    @Test
    void ReglasDeEstructura_lector_NuncaPuede() {
        como(RolAcceso.LECTOR);
        conPermiso(PermisoEstructura.ADMINISTRADOR_Y_EDITOR);

        assertThatThrownBy(() -> reglas.verificarPuedeModificar(proceso)).isInstanceOf(AccessDeniedException.class);
    }

    // Escenario: un editor intenta modificar.
    // Esperado: permitido si el pool admite editores; denegado si es solo administrador.
    @Test
    void ReglasDeEstructura_editor_SegunElPermisoDelPoolPropietario() {
        como(RolAcceso.EDITOR);
        conPermiso(PermisoEstructura.ADMINISTRADOR_Y_EDITOR);
        assertThatCode(() -> reglas.verificarPuedeModificar(proceso)).doesNotThrowAnyException();

        // Se cambia el permiso del pool y se repite la acción: ahora debe denegar.
        conPermiso(PermisoEstructura.SOLO_ADMINISTRADOR);
        assertThatThrownBy(() -> reglas.verificarPuedeModificar(proceso)).isInstanceOf(AccessDeniedException.class);
    }

    // Escenario: no hay usuario en sesión.
    // Esperado: AccessDeniedException.
    @Test
    void ReglasDeEstructura_sinUsuarioAutenticado_Rechaza() {
        when(contextoDeSeguridad.actual()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reglas.verificarPuedeModificar(proceso)).isInstanceOf(AccessDeniedException.class);
    }
}

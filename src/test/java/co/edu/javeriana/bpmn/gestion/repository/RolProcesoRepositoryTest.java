package co.edu.javeriana.bpmn.gestion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import co.edu.javeriana.bpmn.config.AuditoriaConfig;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoResumen;
import co.edu.javeriana.bpmn.gestion.dto.UsoDeRol;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.RolProceso;
import co.edu.javeriana.bpmn.modelado.entity.Lane;
import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoPool;

/**
 * Pruebas del repositorio de roles de proceso con @DataJpaTest (JPA + H2): consultas que dicen qué procesos
 * usan un rol y el catálogo de roles activos.
 */
@DataJpaTest
@ActiveProfiles("test")
// Activa la auditoría automática.
@Import(AuditoriaConfig.class)
class RolProcesoRepositoryTest {

    // Empresa de trabajo.
    private static final Long EMPRESA = 1L;

    // Repositorio bajo prueba y EntityManager de pruebas para armar datos.
    @Autowired
    private RolProcesoRepository rolRepository;

    @Autowired
    private TestEntityManager em;

    // Datos compartidos por las pruebas.
    private RolProceso analista;
    private RolProceso auditor;
    private Proceso vacaciones;
    private Proceso compras;

    // Antes de cada prueba: dos roles, dos procesos con su pool, y lanes: Analista en ambos procesos, Auditor en una lane inactiva.
    @BeforeEach
    void datos() {
        analista = guardar(RolProceso.builder().nombre("Analista").build());
        auditor = guardar(RolProceso.builder().nombre("Auditor").build());
        vacaciones = guardar(Proceso.builder().nombre("Vacaciones").categoria("TH").build());
        compras = guardar(Proceso.builder().nombre("Compras").categoria("C").build());
        Pool poolVacaciones = guardar(Pool.builder().proceso(vacaciones).nombre("A").tipo(TipoPool.PROPIETARIO).build());
        Pool poolCompras = guardar(Pool.builder().proceso(compras).nombre("A").tipo(TipoPool.PROPIETARIO).build());
        guardar(Lane.builder().pool(poolVacaciones).rol(analista).build());
        guardar(Lane.builder().pool(poolCompras).rol(analista).build());
        guardar(Lane.builder().pool(poolCompras).rol(auditor).activo(false).build());
        em.flush();
    }

    // Auxiliar: asigna la empresa y persiste cualquier entidad de empresa.
    private <T extends co.edu.javeriana.bpmn.common.entity.EntidadDeEmpresa> T guardar(T entidad) {
        entidad.setEmpresaId(EMPRESA);
        return em.persist(entidad);
    }

    // Escenario: consultar qué procesos usan el rol Analista.
    // Esperado: Compras y Vacaciones, sin repetir y ordenados, y 2 lanes activas.
    @Test
    void RolProcesoRepository_procesosQueLoUsan_NamedQueryOrdenadaYSinRepetir() {
        assertThat(rolRepository.procesosQueLoUsan(analista.getId()))
                .extracting(ProcesoResumen::nombre).containsExactly("Compras", "Vacaciones");
        assertThat(rolRepository.contarLanesActivasQueLoUsan(analista.getId())).isEqualTo(2);
    }

    // Escenario: el único uso del rol Auditor es una lane inactiva.
    // Esperado: no cuenta como uso.
    @Test
    void RolProcesoRepository_laneInactiva_NoCuentaComoUso() {
        assertThat(rolRepository.contarLanesActivasQueLoUsan(auditor.getId())).isZero();
        assertThat(rolRepository.procesosQueLoUsan(auditor.getId())).isEmpty();
    }

    // Escenario: se elimina (lógico) el proceso Compras.
    // Esperado: ya no aparece como proceso que usa el rol.
    @Test
    void RolProcesoRepository_procesoEliminado_NoCuentaComoUso() {
        compras.setActivo(false);
        em.flush();

        assertThat(rolRepository.procesosQueLoUsan(analista.getId()))
                .extracting(ProcesoResumen::nombre).containsExactly("Vacaciones");
    }

    // Escenario: pedir el uso de varios roles a la vez.
    // Esperado: una consulta con 2 filas, ambas del rol Analista (Auditor no tiene uso activo).
    @Test
    void RolProcesoRepository_usoDeRoles_UnaConsultaParaVariosRoles() {
        List<UsoDeRol> uso = rolRepository.usoDeRoles(List.of(analista.getId(), auditor.getId()));

        assertThat(uso).hasSize(2).allMatch(u -> u.rolId().equals(analista.getId()));
    }

    // Escenario: desactivar un rol y crear otro.
    // Esperado: el catálogo solo lista los activos, ordenados por nombre.
    @Test
    void RolProcesoRepository_catalogo_SoloActivosOrdenados() {
        auditor.setActivo(false);
        guardar(RolProceso.builder().nombre("Supervisor").build());
        em.flush();

        assertThat(rolRepository.findByEmpresaIdAndActivoTrueOrderByNombreAsc(EMPRESA))
                .extracting(RolProceso::getNombre).containsExactly("Analista", "Supervisor");
    }
}

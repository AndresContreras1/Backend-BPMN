package co.edu.javeriana.bpmn.gestion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import co.edu.javeriana.bpmn.config.AuditoriaConfig;
import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.EstadoProceso;
import co.edu.javeriana.bpmn.gestion.entity.HistorialCambio;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.ProcesoCompartido;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;
import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoPool;
import co.edu.javeriana.bpmn.modelado.repository.PoolRepository;

/**
 * Pruebas del repositorio de procesos con @DataJpaTest (JPA + H2 en memoria, transacción revertida por prueba).
 * Cubre la búsqueda con filtros, aislamiento por empresa, consultas con nombre y cascadas.
 */
@DataJpaTest
@ActiveProfiles("test")
// Activa la auditoría automática de las entidades.
@Import(AuditoriaConfig.class)
class ProcesoRepositoryTest {

    // Dos empresas para comprobar que los datos no se mezclan.
    private static final Long EMPRESA_A = 1L;
    private static final Long EMPRESA_B = 2L;

    // Repositorios reales y TestEntityManager para operaciones manuales.
    @Autowired
    private ProcesoRepository procesoRepository;

    @Autowired
    private PoolRepository poolRepository;

    @Autowired
    private HistorialCambioRepository historialRepository;

    @Autowired
    private TestEntityManager entityManager;

    // Proceso reutilizado por varias pruebas.
    private Proceso vacaciones;

    // Antes de cada prueba: 3 procesos en la empresa A (uno inactivo, es decir eliminado) y 1 en la B.
    @BeforeEach
    void datos() {
        vacaciones = proceso("Solicitud de vacaciones", "Talento humano", EstadoProceso.PUBLICADO, EMPRESA_A, true);
        proceso("Aprobación de compras", "Compras", EstadoProceso.BORRADOR, EMPRESA_A, true);
        proceso("Proceso obsoleto", "Compras", EstadoProceso.BORRADOR, EMPRESA_A, false);
        proceso("Solicitud de vacaciones", "Talento humano", EstadoProceso.BORRADOR, EMPRESA_B, true);
    }

    // Auxiliar: crea y guarda un proceso.
    private Proceso proceso(String nombre, String categoria, EstadoProceso estado, Long empresa, boolean activo) {
        Proceso p = Proceso.builder().nombre(nombre).categoria(categoria).estado(estado).activo(activo).build();
        p.setEmpresaId(empresa);
        return procesoRepository.save(p);
    }

    // Auxiliar: ejecuta la búsqueda con filtros ordenada por nombre.
    private Page<Proceso> buscar(Long empresa, String nombre, EstadoProceso estado, String categoria, boolean activo) {
        return procesoRepository.buscar(empresa, nombre, estado, categoria, activo, PageRequest.of(0, 10, Sort.by("nombre")));
    }

    // Escenario: buscar sin filtros en la empresa A.
    // Esperado: solo los activos de esa empresa, ordenados por nombre (no el eliminado ni los de la B).
    @Test
    void ProcesoRepository_buscarSinFiltros_SoloActivosDeLaEmpresa() {
        assertThat(buscar(EMPRESA_A, "", null, "", true).getContent())
                .extracting(Proceso::getNombre)
                .containsExactly("Aprobación de compras", "Solicitud de vacaciones");
    }

    // Escenario: buscar "VACA" en mayúsculas.
    // Esperado: 1 resultado (coincidencia parcial sin distinguir mayúsculas).
    @Test
    void ProcesoRepository_buscarPorNombreParcialSinMayusculas() {
        assertThat(buscar(EMPRESA_A, "VACA", null, "", true).getTotalElements()).isEqualTo(1);
    }

    // Escenario: filtrar por estado, por categoría (con distinta capitalización) y por una combinación sin coincidencias.
    // Esperado: un resultado, un resultado y lista vacía.
    @Test
    void ProcesoRepository_buscarPorEstadoYCategoria() {
        assertThat(buscar(EMPRESA_A, "", EstadoProceso.BORRADOR, "", true).getContent())
                .extracting(Proceso::getNombre).containsExactly("Aprobación de compras");
        assertThat(buscar(EMPRESA_A, "", null, "talento HUMANO", true).getContent())
                .extracting(Proceso::getNombre).containsExactly("Solicitud de vacaciones");
        assertThat(buscar(EMPRESA_A, "", EstadoProceso.PUBLICADO, "Compras", true).getContent()).isEmpty();
    }

    // Escenario: buscar con activo=false.
    // Esperado: aparece el proceso eliminado.
    @Test
    void ProcesoRepository_buscarInactivos_MuestraLosEliminados() {
        assertThat(buscar(EMPRESA_A, "", null, "", false).getContent())
                .extracting(Proceso::getNombre).containsExactly("Proceso obsoleto");
    }

    // Escenario: pedir páginas de tamaño 1.
    // Esperado: 1 elemento por página, total 2 y 2 páginas.
    @Test
    void ProcesoRepository_paginacion_CuentaTotales() {
        Page<Proceso> pagina = procesoRepository.buscar(EMPRESA_A, "", null, "", true, PageRequest.of(0, 1));

        assertThat(pagina.getContent()).hasSize(1);
        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getTotalPages()).isEqualTo(2);
    }

    // Escenario: buscar un proceso con la empresa equivocada y con la correcta.
    // Esperado: vacío y presente (aislamiento por empresa).
    @Test
    void ProcesoRepository_findByIdAndEmpresaId_NoDevuelveProcesoDeOtraEmpresa() {
        assertThat(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(vacaciones.getId(), EMPRESA_B)).isEmpty();
        assertThat(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(vacaciones.getId(), EMPRESA_A)).isPresent();
    }

    // Escenario: comprobar la regla de nombre único.
    // Esperado: choca con uno activo (sin importar mayúsculas), no choca con el eliminado y no choca consigo mismo al excluir su id.
    @Test
    void ProcesoRepository_unicidadSoloEntreActivosDeLaMismaEmpresa() {
        assertThat(procesoRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrue("solicitud DE vacaciones", EMPRESA_A)).isTrue();
        assertThat(procesoRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrue("Proceso obsoleto", EMPRESA_A)).isFalse();
        assertThat(procesoRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrueAndIdNot(
                "Solicitud de vacaciones", EMPRESA_A, vacaciones.getId())).isFalse();
    }

    // Escenario: listar las categorías de la empresa A con la consulta con nombre.
    // Esperado: categorías distintas y ordenadas.
    @Test
    void ProcesoRepository_namedQueryCategorias_DistintasYOrdenadas() {
        assertThat(procesoRepository.listarCategoriasDeEmpresa(EMPRESA_A)).containsExactly("Compras", "Talento humano");
    }

    // Escenario: contar procesos activos por estado.
    // Esperado: 1 borrador y 1 publicado.
    @Test
    void ProcesoRepository_namedQueryContarPorEstado() {
        assertThat(procesoRepository.contarPorEstado(EMPRESA_A, EstadoProceso.BORRADOR)).isEqualTo(1);
        assertThat(procesoRepository.contarPorEstado(EMPRESA_A, EstadoProceso.PUBLICADO)).isEqualTo(1);
    }

    // Escenario: contar eliminados por empresa.
    // Esperado: 1 en la A y 0 en la B.
    @Test
    void ProcesoRepository_contarEliminados_SoloLosInactivosDeLaEmpresa() {
        assertThat(procesoRepository.countByEmpresaIdAndActivoFalse(EMPRESA_A)).isEqualTo(1);
        assertThat(procesoRepository.countByEmpresaIdAndActivoFalse(EMPRESA_B)).isZero();
    }

    // Escenario: se comparte un proceso con la empresa B (activo) y otro con la comparación retirada.
    // Esperado: la empresa B cuenta 1 y la A cuenta 0.
    @Test
    void ProcesoRepository_contarCompartidosCon_IgnoraLasComparticionesRetiradas() {
        compartir(vacaciones, EMPRESA_B, true);
        Proceso compras = buscar(EMPRESA_A, "compras", null, "", true).getContent().get(0);
        compartir(compras, EMPRESA_B, false);

        assertThat(procesoRepository.contarCompartidosCon(EMPRESA_B)).isEqualTo(1);
        assertThat(procesoRepository.contarCompartidosCon(EMPRESA_A)).isZero();
    }

    // Auxiliar: guarda un registro de proceso compartido con la empresa invitada.
    private void compartir(Proceso proceso, Long empresaInvitada, boolean activo) {
        ProcesoCompartido c = ProcesoCompartido.builder().proceso(proceso).empresaInvitadaId(empresaInvitada)
                .activo(activo).build();
        c.setEmpresaId(proceso.getEmpresaId());
        entityManager.persist(c);
    }

    // Escenario: editar y guardar un proceso.
    // Esperado: la versión (bloqueo optimista) sube en uno.
    @Test
    void Proceso_version_AumentaConCadaEdicion() {
        Long inicial = vacaciones.getVersion();
        vacaciones.setDescripcion("Nueva");
        procesoRepository.saveAndFlush(vacaciones);

        assertThat(vacaciones.getVersion()).isEqualTo(inicial + 1);
    }

    // Escenario: borrar físicamente un proceso que tiene pool e historial.
    // Esperado: la base elimina también el pool y el historial en cascada.
    @Test
    void Proceso_borradoFisico_LaBaseEliminaEnCascadaSusPoolsEHistorial() {
        Pool pool = Pool.builder().proceso(vacaciones).nombre("Empresa A").tipo(TipoPool.PROPIETARIO).orden(0).build();
        pool.setEmpresaId(EMPRESA_A);
        poolRepository.save(pool);
        HistorialCambio cambio = HistorialCambio.builder().proceso(vacaciones).usuarioCorreo("sistema")
                .fecha(java.time.Instant.now()).tipo(TipoCambio.CREACION).elemento(ElementoHistorial.PROCESO)
                .detalle("creado").build();
        cambio.setEmpresaId(EMPRESA_A);
        historialRepository.save(cambio);
        entityManager.flush();
        entityManager.clear();

        // Se usa SQL nativo para que actúe la cascada de la base y no la de JPA.
        entityManager.getEntityManager()
                .createNativeQuery("delete from proceso where id = :id").setParameter("id", vacaciones.getId())
                .executeUpdate();

        assertThat(poolRepository.findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(vacaciones.getId())).isEmpty();
        assertThat(historialRepository.findByProcesoIdAndEmpresaId(vacaciones.getId(), EMPRESA_A,
                PageRequest.of(0, 10)).getTotalElements()).isZero();
    }
}

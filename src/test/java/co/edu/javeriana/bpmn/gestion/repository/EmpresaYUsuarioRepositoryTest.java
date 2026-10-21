package co.edu.javeriana.bpmn.gestion.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import co.edu.javeriana.bpmn.config.AuditoriaConfig;
import co.edu.javeriana.bpmn.gestion.entity.Empresa;
import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;

/**
 * Pruebas de los repositorios de empresas y usuarios con @DataJpaTest: levanta solo JPA con H2 en memoria.
 * Cada prueba corre en una transacción que se revierte al terminar.
 */
@DataJpaTest
@ActiveProfiles("test")
// Se importa la configuración de auditoría para que se llenen los campos creadoPor, creadoEn, etc.
@Import(AuditoriaConfig.class)
class EmpresaYUsuarioRepositoryTest {

    // Repositorios reales sobre H2; TestEntityManager sirve para forzar flush y consultar directamente.
    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TestEntityManager entityManager;

    // Dos empresas de prueba para verificar el aislamiento.
    private Empresa empresaA;
    private Empresa empresaB;

    // Antes de cada prueba: se guardan dos empresas y tres usuarios (dos en la empresa A, uno en la B).
    @BeforeEach
    void datos() {
        empresaA = empresaRepository.save(Empresa.builder()
                .nombre("Empresa A").nit("900123456-1").correoContacto("contacto@a.com").build());
        empresaB = empresaRepository.save(Empresa.builder()
                .nombre("Empresa B").nit("800987654-3").correoContacto("contacto@b.com").build());
        usuarioRepository.save(usuario("Ana", "ana@a.com", empresaA, RolAcceso.ADMINISTRADOR));
        usuarioRepository.save(usuario("Andrés", "andres@a.com", empresaA, RolAcceso.EDITOR));
        usuarioRepository.save(usuario("Bea", "bea@b.com", empresaB, RolAcceso.ADMINISTRADOR));
    }

    // Auxiliar: arma un usuario con clave cifrada falsa en la empresa dada.
    private static Usuario usuario(String nombre, String correo, Empresa empresa, RolAcceso rol) {
        Usuario u = Usuario.builder().nombre(nombre).correo(correo)
                .password("$2a$10$hashDePruebaQueNoEsReal000000000000000000000000000000").rol(rol).build();
        u.setEmpresaId(empresa.getId());
        return u;
    }

    // Escenario: guardar una empresa.
    // Esperado: fechas y autor de auditoría llenos y activo por defecto.
    // Se recarga la empresa y se comprueba que creadoEn, modificadoEn y creadoPor se llenaron solos.
    @Test
    void EmpresaRepository_save_LlenaCamposDeAuditoria() {
        Empresa guardada = entityManager.find(Empresa.class, empresaA.getId());

        assertThat(guardada.getCreadoEn()).isNotNull();
        assertThat(guardada.getModificadoEn()).isNotNull();
        assertThat(guardada.getCreadoPor()).isEqualTo(AuditoriaConfig.AUDITOR_SISTEMA);
        assertThat(guardada.isActivo()).isTrue();
    }

    // Escenario: preguntar si existe un NIT.
    // Esperado: true para el existente, false para otro.
    @Test
    void EmpresaRepository_existsByNitIgnoreCase_DetectaDuplicado() {
        assertThat(empresaRepository.existsByNitIgnoreCase("900123456-1")).isTrue();
        assertThat(empresaRepository.existsByNitIgnoreCase("111")).isFalse();
    }

    // Escenario: guardar otra empresa con el mismo NIT.
    // Esperado: la base lanza DataIntegrityViolationException (restricción de unicidad).
    @Test
    void EmpresaRepository_nitRepetido_LaBaseLoRechaza() {
        Empresa copia = Empresa.builder().nombre("Copia").nit("900123456-1").correoContacto("otro@a.com").build();

        assertThatThrownBy(() -> {
            // flush envía el INSERT a la base de inmediato; sin él el error de unicidad no ocurriría dentro de la prueba.
            empresaRepository.save(copia);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // Escenario: se desactiva la empresa B y se buscan ambos NIT.
    // Esperado: solo vuelve la empresa A (activa).
    @Test
    void EmpresaRepository_findByNitIn_SoloEmpresasActivas() {
        empresaB.setActivo(false);
        entityManager.flush();

        List<Empresa> encontradas = empresaRepository.findByNitInAndActivoTrue(List.of("900123456-1", "800987654-3"));

        assertThat(encontradas).extracting(Empresa::getNombre).containsExactly("Empresa A");
    }

    // Escenario: buscar por correo en mayúsculas y uno inexistente.
    // Esperado: el primero se encuentra, el segundo no.
    @Test
    void UsuarioRepository_findByCorreoIgnoreCase_EncuentraSinImportarMayusculas() {
        assertThat(usuarioRepository.findByCorreoIgnoreCase("ANA@A.COM")).isPresent();
        assertThat(usuarioRepository.findByCorreoIgnoreCase("nadie@a.com")).isEmpty();
    }

    // Escenario: buscar al usuario de la empresa B pidiendo la empresa A.
    // Esperado: vacío; con su empresa real sí aparece (aislamiento por empresa).
    @Test
    void UsuarioRepository_findByIdAndEmpresaId_NoDevuelveUsuarioDeOtraEmpresa() {
        Usuario bea = usuarioRepository.findByCorreoIgnoreCase("bea@b.com").orElseThrow();

        assertThat(usuarioRepository.findByIdAndEmpresaId(bea.getId(), empresaA.getId())).isEmpty();
        assertThat(usuarioRepository.findByIdAndEmpresaId(bea.getId(), empresaB.getId())).isPresent();
    }

    // Escenario: buscar usuarios de la empresa A cuyo nombre contenga "an".
    // Esperado: 2 resultados (Ana y Andrés), todos de esa empresa.
    @Test
    void UsuarioRepository_listadoPaginado_SoloDeLaEmpresaYFiltradoPorNombre() {
        Page<Usuario> pagina = usuarioRepository
                .findByEmpresaIdAndNombreContainingIgnoreCase(empresaA.getId(), "an", PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getContent()).allMatch(u -> u.getEmpresaId().equals(empresaA.getId()));
    }

    // Escenario: contar administradores activos antes y después de desactivar al único.
    // Esperado: 1 y luego 0.
    @Test
    void UsuarioRepository_countAdministradoresActivos_IgnoraInactivos() {
        assertThat(usuarioRepository.countByEmpresaIdAndRolAndActivoTrue(empresaA.getId(), RolAcceso.ADMINISTRADOR))
                .isEqualTo(1);

        Usuario ana = usuarioRepository.findByCorreoIgnoreCase("ana@a.com").orElseThrow();
        ana.setActivo(false);
        entityManager.flush();

        assertThat(usuarioRepository.countByEmpresaIdAndRolAndActivoTrue(empresaA.getId(), RolAcceso.ADMINISTRADOR))
                .isZero();
    }

    // Escenario: guardar otro usuario con un correo ya usado (aunque sea de otra empresa).
    // Esperado: DataIntegrityViolationException (el correo es único en todo el sistema).
    @Test
    void UsuarioRepository_correoRepetido_LaBaseLoRechaza() {
        Usuario copia = usuario("Ana 2", "ana@a.com", empresaB, RolAcceso.LECTOR);

        assertThatThrownBy(() -> {
            // flush fuerza el INSERT para que el error de unicidad se produzca aquí.
            usuarioRepository.save(copia);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // Escenario: intentar cambiar la empresa de un usuario ya guardado.
    // Esperado: tras recargar desde la base, sigue en su empresa original (el campo no es actualizable).
    @Test
    void Usuario_empresaIdNoActualizable_UnUsuarioNoCambiaDeEmpresa() {
        Usuario ana = usuarioRepository.findByCorreoIgnoreCase("ana@a.com").orElseThrow();
        ana.setEmpresaId(empresaB.getId());
        entityManager.flush();
        // clear vacía la memoria del EntityManager para que find lea de verdad desde la base.
        entityManager.clear();

        Usuario recargada = entityManager.find(Usuario.class, ana.getId());

        assertThat(recargada.getEmpresaId()).isEqualTo(empresaA.getId());
    }

    // Escenario: imprimir un usuario.
    // Esperado: el texto no contiene la contraseña ni su hash (evita filtrarla en logs).
    @Test
    void Usuario_toString_NoIncluyeLaContrasena() {
        Usuario ana = usuarioRepository.findByCorreoIgnoreCase("ana@a.com").orElseThrow();

        assertThat(ana.toString()).doesNotContain("password").doesNotContain("$2a$");
    }
}

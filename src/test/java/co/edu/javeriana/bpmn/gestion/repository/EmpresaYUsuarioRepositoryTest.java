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

@DataJpaTest
@ActiveProfiles("test")
@Import(AuditoriaConfig.class)
class EmpresaYUsuarioRepositoryTest {

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Empresa empresaA;
    private Empresa empresaB;

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

    private static Usuario usuario(String nombre, String correo, Empresa empresa, RolAcceso rol) {
        Usuario u = Usuario.builder().nombre(nombre).correo(correo)
                .password("$2a$10$hashDePruebaQueNoEsReal000000000000000000000000000000").rol(rol).build();
        u.setEmpresaId(empresa.getId());
        return u;
    }

    @Test
    void EmpresaRepository_save_LlenaCamposDeAuditoria() {
        Empresa guardada = entityManager.find(Empresa.class, empresaA.getId());

        assertThat(guardada.getCreadoEn()).isNotNull();
        assertThat(guardada.getModificadoEn()).isNotNull();
        assertThat(guardada.getCreadoPor()).isEqualTo(AuditoriaConfig.AUDITOR_SISTEMA);
        assertThat(guardada.isActivo()).isTrue();
    }

    @Test
    void EmpresaRepository_existsByNitIgnoreCase_DetectaDuplicado() {
        assertThat(empresaRepository.existsByNitIgnoreCase("900123456-1")).isTrue();
        assertThat(empresaRepository.existsByNitIgnoreCase("111")).isFalse();
    }

    @Test
    void EmpresaRepository_nitRepetido_LaBaseLoRechaza() {
        Empresa copia = Empresa.builder().nombre("Copia").nit("900123456-1").correoContacto("otro@a.com").build();

        assertThatThrownBy(() -> {
            empresaRepository.save(copia);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void EmpresaRepository_findByNitIn_SoloEmpresasActivas() {
        empresaB.setActivo(false);
        entityManager.flush();

        List<Empresa> encontradas = empresaRepository.findByNitInAndActivoTrue(List.of("900123456-1", "800987654-3"));

        assertThat(encontradas).extracting(Empresa::getNombre).containsExactly("Empresa A");
    }

    @Test
    void UsuarioRepository_findByCorreoIgnoreCase_EncuentraSinImportarMayusculas() {
        assertThat(usuarioRepository.findByCorreoIgnoreCase("ANA@A.COM")).isPresent();
        assertThat(usuarioRepository.findByCorreoIgnoreCase("nadie@a.com")).isEmpty();
    }

    @Test
    void UsuarioRepository_findByIdAndEmpresaId_NoDevuelveUsuarioDeOtraEmpresa() {
        Usuario bea = usuarioRepository.findByCorreoIgnoreCase("bea@b.com").orElseThrow();

        assertThat(usuarioRepository.findByIdAndEmpresaId(bea.getId(), empresaA.getId())).isEmpty();
        assertThat(usuarioRepository.findByIdAndEmpresaId(bea.getId(), empresaB.getId())).isPresent();
    }

    @Test
    void UsuarioRepository_listadoPaginado_SoloDeLaEmpresaYFiltradoPorNombre() {
        Page<Usuario> pagina = usuarioRepository
                .findByEmpresaIdAndNombreContainingIgnoreCase(empresaA.getId(), "an", PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getContent()).allMatch(u -> u.getEmpresaId().equals(empresaA.getId()));
    }

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

    @Test
    void UsuarioRepository_correoRepetido_LaBaseLoRechaza() {
        Usuario copia = usuario("Ana 2", "ana@a.com", empresaB, RolAcceso.LECTOR);

        assertThatThrownBy(() -> {
            usuarioRepository.save(copia);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void Usuario_empresaIdNoActualizable_UnUsuarioNoCambiaDeEmpresa() {
        Usuario ana = usuarioRepository.findByCorreoIgnoreCase("ana@a.com").orElseThrow();
        ana.setEmpresaId(empresaB.getId());
        entityManager.flush();
        entityManager.clear();

        Usuario recargada = entityManager.find(Usuario.class, ana.getId());

        assertThat(recargada.getEmpresaId()).isEqualTo(empresaA.getId());
    }

    @Test
    void Usuario_toString_NoIncluyeLaContrasena() {
        Usuario ana = usuarioRepository.findByCorreoIgnoreCase("ana@a.com").orElseThrow();

        assertThat(ana.toString()).doesNotContain("password").doesNotContain("$2a$");
    }
}

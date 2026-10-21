package co.edu.javeriana.bpmn.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.gestion.entity.Empresa;
import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;
import co.edu.javeriana.bpmn.gestion.repository.EmpresaRepository;
import co.edu.javeriana.bpmn.gestion.repository.UsuarioRepository;

/**
 * Datos de demostración para desarrollo (perfil default) y para la imagen Docker (perfil demo).
 * Es el único punto donde se usan los repositorios sin pasar por los servicios.
 * Es idempotente: si ya hay empresas, no hace nada.
 */
@Component
@Profile({"default", "demo"})
@Transactional // muchas consultas: commit al final o rollback completo
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DataLoader(EmpresaRepository empresaRepository, UsuarioRepository usuarioRepository,
                      PasswordEncoder passwordEncoder) {
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (empresaRepository.count() > 0) {
            log.info("Datos de demostración ya cargados");
            return;
        }

        Empresa demo = empresaRepository.save(Empresa.builder()
                .nombre("Facimus Demo S.A.S.").nit("900123456-1").correoContacto("admin@demo.com").build());
        usuario(demo, "Administradora Demo", "admin@demo.com", "admin123", RolAcceso.ADMINISTRADOR);
        usuario(demo, "Editor Demo", "editor@demo.com", "editor123", RolAcceso.EDITOR);
        usuario(demo, "Lector Demo", "lector@demo.com", "lector123", RolAcceso.LECTOR);

        Empresa andina = empresaRepository.save(Empresa.builder()
                .nombre("Comercial Andina Ltda.").nit("800987654-3").correoContacto("admin@andina.com").build());
        usuario(andina, "Administrador Andina", "admin@andina.com", "admin123", RolAcceso.ADMINISTRADOR);
        usuario(andina, "Editora Andina", "editor@andina.com", "editor123", RolAcceso.EDITOR);
        usuario(andina, "Lector Andina", "lector@andina.com", "lector123", RolAcceso.LECTOR);

        log.info("Datos de demostración cargados: 2 empresas y 6 usuarios");
    }

    private Usuario usuario(Empresa empresa, String nombre, String correo, String clave, RolAcceso rol) {
        Usuario usuario = Usuario.builder()
                .nombre(nombre).correo(correo).password(passwordEncoder.encode(clave)).rol(rol).build();
        usuario.setEmpresaId(empresa.getId());
        return usuarioRepository.save(usuario);
    }
}

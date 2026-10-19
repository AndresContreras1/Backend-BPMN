package co.edu.javeriana.bpmn.gestion.service.impl;

import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.dto.PageResponse;
import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.common.util.Paginacion;
import co.edu.javeriana.bpmn.gestion.dto.CambioRolRequest;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioRequest;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioResponse;
import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;
import co.edu.javeriana.bpmn.gestion.repository.UsuarioRepository;
import co.edu.javeriana.bpmn.gestion.service.UsuarioService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private static final Set<String> ORDEN_PERMITIDO = Set.of("nombre", "correo", "rol", "activo", "creadoEn");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UsuarioResponse> listar(Long empresaId, String nombre, Pageable pageable) {
        Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        String filtro = nombre == null ? "" : nombre.trim();
        return PageResponse.de(
                usuarioRepository.findByEmpresaIdAndNombreContainingIgnoreCase(empresaId, filtro, pageable),
                UsuarioResponse::de);
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id, Long empresaId) {
        return UsuarioResponse.de(obtenerDeEmpresa(id, empresaId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public UsuarioResponse crear(UsuarioRequest request, Long empresaId) {
        String correo = request.correo().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new ReglaDeNegocioException("El correo " + correo + " ya está registrado como usuario");
        }
        Usuario usuario = Usuario.builder()
                .nombre(request.nombre().trim())
                .correo(correo)
                .password(passwordEncoder.encode(request.passwordInicial()))
                .rol(request.rol())
                .build();
        usuario.setEmpresaId(empresaId);
        return UsuarioResponse.de(usuarioRepository.save(usuario));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public UsuarioResponse cambiarRol(Long id, CambioRolRequest request, Long empresaId, Long actorId) {
        Usuario usuario = obtenerDeEmpresa(id, empresaId);
        if (usuario.getId().equals(actorId)) {
            throw new ReglaDeNegocioException("No puede cambiar su propio rol de acceso");
        }
        if (request.rol() != RolAcceso.ADMINISTRADOR) {
            exigirOtroAdministradorActivo(usuario);
        }
        usuario.setRol(request.rol());
        return UsuarioResponse.de(usuario);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public UsuarioResponse desactivar(Long id, Long empresaId, Long actorId) {
        Usuario usuario = obtenerDeEmpresa(id, empresaId);
        if (usuario.getId().equals(actorId)) {
            throw new ReglaDeNegocioException("No puede desactivar su propia cuenta");
        }
        exigirOtroAdministradorActivo(usuario);
        // Desactivar no borra: el historial y los procesos del usuario se conservan (son de la empresa)
        usuario.setActivo(false);
        return UsuarioResponse.de(usuario);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public UsuarioResponse activar(Long id, Long empresaId) {
        Usuario usuario = obtenerDeEmpresa(id, empresaId);
        usuario.setActivo(true);
        return UsuarioResponse.de(usuario);
    }

    /** Único punto de carga: filtra por empresa en la consulta y responde 404 a lo ajeno. */
    private Usuario obtenerDeEmpresa(Long id, Long empresaId) {
        return usuarioRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new NotFoundException("No existe el usuario " + id));
    }

    private void exigirOtroAdministradorActivo(Usuario usuario) {
        boolean esAdministradorActivo = usuario.getRol() == RolAcceso.ADMINISTRADOR && usuario.isActivo();
        if (esAdministradorActivo
                && usuarioRepository.countByEmpresaIdAndRolAndActivoTrue(usuario.getEmpresaId(), RolAcceso.ADMINISTRADOR) <= 1) {
            throw new ReglaDeNegocioException("La empresa debe conservar al menos un administrador activo");
        }
    }
}

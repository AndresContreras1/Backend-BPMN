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
import co.edu.javeriana.bpmn.gestion.dto.CambioEstadoRequest;
import co.edu.javeriana.bpmn.gestion.dto.CambioRolRequest;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioRequest;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioResponse;
import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;
import co.edu.javeriana.bpmn.gestion.repository.UsuarioRepository;
import co.edu.javeriana.bpmn.gestion.service.UsuarioService;
import lombok.RequiredArgsConstructor;

/** Implementación de la gestión de usuarios (HU-02): listar, crear, cambiar rol y activar o desactivar. */
// @Service: bean de Spring. @RequiredArgsConstructor: constructor con los campos final para la inyección.
@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    /** Campos por los que se permite ordenar la lista (evita ordenar por un campo que no existe o es sensible). */
    private static final Set<String> ORDEN_PERMITIDO = Set.of("nombre", "correo", "rol", "activo", "creadoEn");

    /** Acceso a la tabla de usuarios. */
    private final UsuarioRepository usuarioRepository;
    /** Cifra las contraseñas (BCrypt) antes de guardarlas. */
    private final PasswordEncoder passwordEncoder;

    /** Lista paginada de usuarios de la empresa, con búsqueda opcional por nombre. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<UsuarioResponse> listar(Long empresaId, String nombre, Pageable pageable) {
        // Valida que el campo de orden pedido esté en la lista permitida.
        Pageable orden = Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        // Un nombre nulo se vuelve "" para que la búsqueda "contiene" traiga todos los usuarios.
        String filtro = nombre == null ? "" : nombre.trim();
        return PageResponse.de(
                usuarioRepository.findByEmpresaIdAndNombreContainingIgnoreCase(empresaId, filtro, orden),
                UsuarioResponse::de);
    }

    /** Devuelve un usuario de la empresa como DTO. */
    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id, Long empresaId) {
        return UsuarioResponse.de(obtenerDeEmpresa(id, empresaId));
    }

    /**
     * Crea un colaborador. @PreAuthorize: Spring Security solo deja entrar si el usuario autenticado
     * tiene la autoridad ADMINISTRADOR; si no, responde 403.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public UsuarioResponse crear(UsuarioRequest request, Long empresaId) {
        // Paso 1: el correo se guarda limpio y en minúsculas, y no puede repetirse en el sistema.
        String correo = request.correo().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new ReglaDeNegocioException("El correo " + correo + " ya está registrado como usuario");
        }
        // Paso 2: arma el usuario con la contraseña cifrada.
        Usuario usuario = Usuario.builder()
                .nombre(request.nombre().trim())
                .correo(correo)
                .password(passwordEncoder.encode(request.passwordInicial()))
                .rol(request.rol())
                .build();
        // Paso 3: la empresa la pone el servidor (la del administrador), no el cliente.
        usuario.setEmpresaId(empresaId);
        return UsuarioResponse.de(usuarioRepository.save(usuario));
    }

    /**
     * Cambia el rol de un usuario (solo administrador). No se puede cambiar el propio rol y la empresa
     * siempre debe conservar al menos un administrador activo.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public UsuarioResponse cambiarRol(Long id, CambioRolRequest request, Long empresaId, Long actorId) {
        Usuario usuario = obtenerDeEmpresa(id, empresaId);
        // Regla: nadie se cambia su propio rol (así un administrador no se quita el acceso por error).
        if (usuario.getId().equals(actorId)) {
            throw new ReglaDeNegocioException("No puede cambiar su propio rol de acceso");
        }
        // Si deja de ser administrador, debe quedar otro administrador activo en la empresa.
        if (request.rol() != RolAcceso.ADMINISTRADOR) {
            exigirOtroAdministradorActivo(usuario);
        }
        // No hay save(): la entidad está "gestionada" y la transacción guarda el cambio al terminar.
        usuario.setRol(request.rol());
        return UsuarioResponse.de(usuario);
    }

    /**
     * Activa o desactiva un usuario (solo administrador). Para desactivar aplican las mismas
     * protecciones: no a uno mismo y siempre debe quedar un administrador activo.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public UsuarioResponse cambiarEstado(Long id, CambioEstadoRequest request, Long empresaId, Long actorId) {
        Usuario usuario = obtenerDeEmpresa(id, empresaId);
        // Las validaciones solo aplican al desactivar; activar siempre se permite.
        if (!request.activo()) {
            if (usuario.getId().equals(actorId)) {
                throw new ReglaDeNegocioException("No puede desactivar su propia cuenta");
            }
            exigirOtroAdministradorActivo(usuario);
        }
        // Desactivar no borra: el historial y los procesos del usuario se conservan (son de la empresa)
        usuario.setActivo(request.activo());
        return UsuarioResponse.de(usuario);
    }

    /** Único punto de carga: filtra por empresa en la consulta y responde 404 a lo ajeno. */
    private Usuario obtenerDeEmpresa(Long id, Long empresaId) {
        return usuarioRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new NotFoundException("No existe el usuario " + id));
    }

    /**
     * Lanza error si el usuario es el único administrador activo de su empresa. Se llama antes de
     * quitarle el rol de administrador o de desactivarlo, para que la empresa nunca quede sin admin.
     */
    private void exigirOtroAdministradorActivo(Usuario usuario) {
        boolean esAdministradorActivo = usuario.getRol() == RolAcceso.ADMINISTRADOR && usuario.isActivo();
        if (esAdministradorActivo
                && usuarioRepository.countByEmpresaIdAndRolAndActivoTrue(usuario.getEmpresaId(), RolAcceso.ADMINISTRADOR) <= 1) {
            throw new ReglaDeNegocioException("La empresa debe conservar al menos un administrador activo");
        }
    }
}

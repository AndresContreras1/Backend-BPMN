package co.edu.javeriana.bpmn.gestion.service;

import org.springframework.data.domain.Pageable;

import co.edu.javeriana.bpmn.common.dto.PageResponse;
import co.edu.javeriana.bpmn.gestion.dto.CambioEstadoRequest;
import co.edu.javeriana.bpmn.gestion.dto.CambioRolRequest;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioRequest;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioResponse;

/** HU-02: gestión de las cuentas de usuario de la empresa. */
public interface UsuarioService {

    /** Lista paginada de los usuarios de la empresa, con búsqueda opcional por nombre. */
    PageResponse<UsuarioResponse> listar(Long empresaId, String nombre, Pageable pageable);

    /** Devuelve un usuario de la empresa (404 si no existe o es de otra empresa). */
    UsuarioResponse obtener(Long id, Long empresaId);

    /** Crea un colaborador en la empresa (solo administrador). */
    UsuarioResponse crear(UsuarioRequest request, Long empresaId);

    /** Cambia el rol de acceso de un usuario; actorId es quien hace el cambio (solo administrador). */
    UsuarioResponse cambiarRol(Long id, CambioRolRequest request, Long empresaId, Long actorId);

    /** Activa o desactiva un usuario; actorId es quien hace el cambio (solo administrador). */
    UsuarioResponse cambiarEstado(Long id, CambioEstadoRequest request, Long empresaId, Long actorId);
}

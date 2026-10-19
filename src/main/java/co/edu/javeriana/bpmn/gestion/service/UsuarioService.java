package co.edu.javeriana.bpmn.gestion.service;

import org.springframework.data.domain.Pageable;

import co.edu.javeriana.bpmn.common.dto.PageResponse;
import co.edu.javeriana.bpmn.gestion.dto.CambioRolRequest;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioRequest;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioResponse;

/** HU-02: gestión de las cuentas de usuario de la empresa. */
public interface UsuarioService {

    PageResponse<UsuarioResponse> listar(Long empresaId, String nombre, Pageable pageable);

    UsuarioResponse obtener(Long id, Long empresaId);

    UsuarioResponse crear(UsuarioRequest request, Long empresaId);

    UsuarioResponse cambiarRol(Long id, CambioRolRequest request, Long empresaId, Long actorId);

    UsuarioResponse desactivar(Long id, Long empresaId, Long actorId);

    UsuarioResponse activar(Long id, Long empresaId);
}

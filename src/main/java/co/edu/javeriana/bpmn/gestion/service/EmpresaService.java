package co.edu.javeriana.bpmn.gestion.service;

import co.edu.javeriana.bpmn.gestion.dto.EmpresaResponse;
import co.edu.javeriana.bpmn.gestion.dto.RegistroEmpresaRequest;
import co.edu.javeriana.bpmn.gestion.dto.RegistroEmpresaResponse;
import co.edu.javeriana.bpmn.gestion.entity.Empresa;

/** Contrato del servicio de empresas: registro (HU-01) y consulta de la empresa propia. */
public interface EmpresaService {

    /** HU-01: registra la empresa como espacio independiente y crea su administrador inicial. */
    RegistroEmpresaResponse registrar(RegistroEmpresaRequest request);

    /** Devuelve los datos de la empresa activa con ese id (404 si no existe). */
    EmpresaResponse obtener(Long empresaId);

    /** Solo la empresa del usuario autenticado; cualquier otro id responde 404. */
    EmpresaResponse obtenerPropia(Long id, Long empresaId);

    /** Devuelve la entidad Empresa (no el DTO); la usan otros servicios que necesitan sus datos internos. */
    Empresa obtenerEntidad(Long empresaId);
}

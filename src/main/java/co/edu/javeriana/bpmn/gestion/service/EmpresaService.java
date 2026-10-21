package co.edu.javeriana.bpmn.gestion.service;

import co.edu.javeriana.bpmn.gestion.dto.EmpresaResponse;
import co.edu.javeriana.bpmn.gestion.dto.RegistroEmpresaRequest;
import co.edu.javeriana.bpmn.gestion.dto.RegistroEmpresaResponse;
import co.edu.javeriana.bpmn.gestion.entity.Empresa;

public interface EmpresaService {

    /** HU-01: registra la empresa como espacio independiente y crea su administrador inicial. */
    RegistroEmpresaResponse registrar(RegistroEmpresaRequest request);

    EmpresaResponse obtener(Long empresaId);

    /** Solo la empresa del usuario autenticado; cualquier otro id responde 404. */
    EmpresaResponse obtenerPropia(Long id, Long empresaId);

    Empresa obtenerEntidad(Long empresaId);
}

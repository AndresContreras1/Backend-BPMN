package co.edu.javeriana.bpmn.gestion.dto;

import java.time.Instant;

import co.edu.javeriana.bpmn.gestion.entity.Empresa;

/**
 * Datos de una empresa que se devuelven al cliente. Se usa un DTO (record) para no exponer
 * directamente la entidad de la base de datos.
 */
public record EmpresaResponse(Long id, String nombre, String nit, String correoContacto, Instant creadoEn) {

    /** Convierte la entidad Empresa en este DTO copiando solo los campos que queremos mostrar. */
    public static EmpresaResponse de(Empresa empresa) {
        return new EmpresaResponse(empresa.getId(), empresa.getNombre(), empresa.getNit(),
                empresa.getCorreoContacto(), empresa.getCreadoEn());
    }
}

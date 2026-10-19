package co.edu.javeriana.bpmn.gestion.dto;

import java.time.Instant;

import co.edu.javeriana.bpmn.gestion.entity.Empresa;

public record EmpresaResponse(Long id, String nombre, String nit, String correoContacto, Instant creadoEn) {

    public static EmpresaResponse de(Empresa empresa) {
        return new EmpresaResponse(empresa.getId(), empresa.getNombre(), empresa.getNit(),
                empresa.getCorreoContacto(), empresa.getCreadoEn());
    }
}

package co.edu.javeriana.bpmn.gestion.dto;

import java.util.List;

/**
 * HU-23: estado de compartición del proceso y empresas que pueden consultarlo.
 * "compartido" es true si hay al menos una empresa con acceso; "empresas" las lista.
 */
public record ComparticionResponse(boolean compartido, List<EmpresaInvitada> empresas) {

    /** Datos públicos de una empresa a la que se le compartió el proceso (nombre y NIT). */
    public record EmpresaInvitada(String nombre, String nit) {
    }
}

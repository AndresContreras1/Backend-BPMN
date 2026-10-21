package co.edu.javeriana.bpmn.gestion.dto;

import java.util.List;

/** HU-23: estado de compartición del proceso y empresas que pueden consultarlo. */
public record ComparticionResponse(boolean compartido, List<EmpresaInvitada> empresas) {

    public record EmpresaInvitada(String nombre, String nit) {
    }
}

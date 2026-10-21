package co.edu.javeriana.bpmn.modelado.dto;

import java.util.List;

/**
 * Gateway guardado y las advertencias que le corresponden: al pasar a exclusivo, el editor
 * advierte si las condiciones no son mutuamente excluyentes (HU-15).
 */
public record GatewayResponse(NodoResponse gateway, List<Advertencia> advertencias) {
}

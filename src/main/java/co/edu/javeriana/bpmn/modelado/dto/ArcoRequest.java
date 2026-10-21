package co.edu.javeriana.bpmn.modelado.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * HU-11 y HU-12: arco entre dos nodos. Cada id se resuelve contra la empresa del usuario;
 * el cuerpo nunca trae empresaId.
 * @param procesoId proceso donde se dibuja el arco, obligatorio
 * @param origenId nodo del que sale, obligatorio
 * @param destinoId nodo al que llega, obligatorio
 * @param etiqueta texto opcional sobre la línea, máximo 80 caracteres
 * @param condicion condición opcional (solo para gateways exclusivo o inclusivo), máximo 200 caracteres
 */
public record ArcoRequest(
        @NotNull(message = "El proceso es obligatorio") Long procesoId,
        @NotNull(message = "El nodo de origen es obligatorio") Long origenId,
        @NotNull(message = "El nodo de destino es obligatorio") Long destinoId,
        @Size(max = 80, message = "La etiqueta admite máximo 80 caracteres") String etiqueta,
        @Size(max = 200, message = "La condición admite máximo 200 caracteres") String condicion) {
}

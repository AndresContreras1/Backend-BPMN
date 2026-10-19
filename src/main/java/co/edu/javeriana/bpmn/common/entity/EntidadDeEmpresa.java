package co.edu.javeriana.bpmn.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Todo dato del sistema cuelga de una empresa (HU-01).
 * El servidor asigna el empresaId a partir del usuario autenticado; nunca llega del cliente
 * y un registro no cambia de empresa (updatable = false).
 */
@MappedSuperclass
@Getter
@Setter
public abstract class EntidadDeEmpresa extends EntidadAuditada {

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;
}

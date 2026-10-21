package co.edu.javeriana.bpmn.common.entity;

import java.time.Instant;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Fechas y autor de creación y última modificación de cada registro.
 * Los llena Spring Data JPA Auditing (ver AuditoriaConfig).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class EntidadAuditada {

    // Momento en que se creó el registro; no se modifica después (updatable = false).
    @CreatedDate
    @Column(name = "creado_en", updatable = false, nullable = false)
    private Instant creadoEn;

    // Momento de la última modificación; se actualiza en cada guardado.
    @LastModifiedDate
    @Column(name = "modificado_en", nullable = false)
    private Instant modificadoEn;

    // Correo de quien creó el registro (lo entrega AuditoriaConfig); no cambia después.
    @CreatedBy
    @Column(name = "creado_por", length = 120, updatable = false, nullable = false)
    private String creadoPor;

    // Correo de quien hizo el último cambio.
    @LastModifiedBy
    @Column(name = "modificado_por", length = 120, nullable = false)
    private String modificadoPor;
}

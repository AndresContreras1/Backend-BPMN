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

    @CreatedDate
    @Column(name = "creado_en", updatable = false, nullable = false)
    private Instant creadoEn;

    @LastModifiedDate
    @Column(name = "modificado_en", nullable = false)
    private Instant modificadoEn;

    @CreatedBy
    @Column(name = "creado_por", length = 120, updatable = false, nullable = false)
    private String creadoPor;

    @LastModifiedBy
    @Column(name = "modificado_por", length = 120, nullable = false)
    private String modificadoPor;
}

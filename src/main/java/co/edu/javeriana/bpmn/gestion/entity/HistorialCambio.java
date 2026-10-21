package co.edu.javeriana.bpmn.gestion.entity;

import java.time.Instant;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import co.edu.javeriana.bpmn.common.entity.EntidadDeEmpresa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Historial de cambios y trazabilidad (HU-05): quién cambió qué elemento, cuándo y cómo.
 * El proceso es opcional porque los cambios de roles de proceso son de la empresa.
 */
@Entity
@Table(name = "historial_cambio", indexes = {
        @Index(name = "idx_historial_proceso", columnList = "proceso_id"),
        @Index(name = "idx_historial_elemento", columnList = "elemento, elemento_id")})
@NamedQuery(name = "HistorialCambio.listarPorElemento",
        query = "select h from HistorialCambio h where h.empresaId = :empresaId "
                + "and h.elemento = :elemento and h.elementoId = :elementoId order by h.fecha desc, h.id desc")
@Getter
@Setter
@ToString(exclude = "proceso")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistorialCambio extends EntidadDeEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Proceso proceso;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "usuario_correo", length = 120, nullable = false)
    private String usuarioCorreo;

    @Column(nullable = false)
    private Instant fecha;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TipoCambio tipo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ElementoHistorial elemento;

    @Column(name = "elemento_id")
    private Long elementoId;

    @Column(length = 1000, nullable = false)
    private String detalle;

    /** Advertencias de coherencia del diagrama en el momento del cambio, separadas por salto de línea. */
    @Column(length = 2000)
    private String advertencias;
}

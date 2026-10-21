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
// Tabla "historial_cambio" con índices por proceso y por (elemento, elemento_id) para consultar rápido el historial.
@Entity
@Table(name = "historial_cambio", indexes = {
        @Index(name = "idx_historial_proceso", columnList = "proceso_id"),
        @Index(name = "idx_historial_elemento", columnList = "elemento, elemento_id")})
// Consulta con nombre: historial de un elemento concreto dentro de la empresa, del más reciente al más antiguo
// (si dos tienen la misma fecha, desempata por id descendente).
@NamedQuery(name = "HistorialCambio.listarPorElemento",
        query = "select h from HistorialCambio h where h.empresaId = :empresaId "
                + "and h.elemento = :elemento and h.elementoId = :elementoId order by h.fecha desc, h.id desc")
// Lombok genera getters, setters, constructores y builder; el toString excluye el proceso para no cargarlo.
@Getter
@Setter
@ToString(exclude = "proceso")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistorialCambio extends EntidadDeEmpresa {

    /** Identificador del registro de historial, autoincremental. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Proceso al que pertenece el cambio (puede ser null en cambios de roles de proceso).
     * LAZY: no se carga hasta que se use. Si el proceso se borra de la base, se borra el historial en cascada.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Proceso proceso;

    /** Id del usuario que hizo el cambio (puede ser null, por ejemplo si el usuario ya no existe). */
    @Column(name = "usuario_id", nullable = true)
    private Long usuarioId;

    /** Correo del usuario en el momento del cambio; se guarda copiado para que el historial no dependa del usuario. */
    @Column(name = "usuario_correo", length = 120, nullable = false)
    private String usuarioCorreo;

    /** Momento exacto en que ocurrió el cambio. */
    @Column(nullable = false)
    private Instant fecha;

    /** Qué tipo de cambio fue (creación, edición, etc.); se guarda como texto. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TipoCambio tipo;

    /** Sobre qué parte del modelo fue el cambio (proceso, pool, lane, etc.); se guarda como texto. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ElementoHistorial elemento;

    /** Id del elemento afectado dentro de su propia tabla (null si no aplica). */
    @Column(name = "elemento_id", nullable = true)
    private Long elementoId;

    /** Texto que describe el cambio realizado. */
    @Column(length = 1000, nullable = false)
    private String detalle;

    /** Advertencias de coherencia del diagrama en el momento del cambio, separadas por salto de línea. */
    @Column(length = 2000, nullable = true)
    private String advertencias;
}

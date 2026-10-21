package co.edu.javeriana.bpmn.modelado.entity;

import co.edu.javeriana.bpmn.common.entity.EntidadDeEmpresa;
import co.edu.javeriana.bpmn.gestion.entity.RolProceso;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Lane (HU-22): división interna del pool que agrupa las actividades de un rol de proceso.
 * Su nombre es el del rol; la actividad que contiene hereda ese responsable.
 */
@Entity
@Table(name = "lane", indexes = {
        @Index(name = "idx_lane_pool", columnList = "pool_id"),
        @Index(name = "idx_lane_rol", columnList = "rol_proceso_id")})
@Getter
@Setter
@ToString(exclude = {"pool", "rol"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lane extends EntidadDeEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pool_id", nullable = false)
    private Pool pool;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rol_proceso_id", nullable = false)
    private RolProceso rol;

    @Column(nullable = false)
    private int orden;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}

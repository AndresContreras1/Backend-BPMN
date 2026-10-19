package co.edu.javeriana.bpmn.modelado.entity;

import co.edu.javeriana.bpmn.common.entity.EntidadDeEmpresa;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Pool (HU-21): un participante del proceso. Todo elemento del diagrama queda dentro de un pool;
 * los arcos nunca cruzan de un pool a otro y los mensajes sí. Un pool de caja negra no tiene
 * elementos internos: solo se documenta lo que se le envía.
 */
@Entity
@Table(name = "pool", indexes = @Index(name = "idx_pool_proceso", columnList = "proceso_id"))
@Getter
@Setter
@ToString(exclude = "proceso")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pool extends EntidadDeEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;

    @Column(length = 120, nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TipoPool tipo;

    @Column(name = "caja_negra", nullable = false)
    private boolean cajaNegra;

    @Column(nullable = false)
    private int orden;

    @Enumerated(EnumType.STRING)
    @Column(name = "permiso_estructura", length = 30, nullable = false)
    @Builder.Default
    private PermisoEstructura permisoEstructura = PermisoEstructura.ADMINISTRADOR_Y_EDITOR;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}

package co.edu.javeriana.bpmn.modelado.entity;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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
import jakarta.persistence.OneToMany;
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
@ToString(exclude = {"proceso", "lanes"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pool extends EntidadDeEmpresa {

    /** Identificador de la fila; la base de datos lo genera sola (autoincremental). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Proceso al que pertenece el pool. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;

    /** Nombre del participante (máximo 120 caracteres). */
    @Column(length = 120, nullable = false)
    private String nombre;

    /** Quién es el participante: propietario, cliente, proveedor o sistema externo. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TipoPool tipo;

    /** Si es true el pool no tiene elementos internos; solo se documentan los mensajes que recibe. */
    @Column(name = "caja_negra", nullable = false)
    private boolean cajaNegra;

    /** Posición del pool en el diagrama; se cambia al reordenar. */
    @Column(nullable = false)
    private int orden;

    /** HU-24: qué roles pueden cambiar la estructura de este pool. Por defecto ADMINISTRADOR_Y_EDITOR. */
    @Enumerated(EnumType.STRING)
    @Column(name = "permiso_estructura", length = 30, nullable = false)
    @Builder.Default
    private PermisoEstructura permisoEstructura = PermisoEstructura.ADMINISTRADOR_Y_EDITOR;

    /** Borrado lógico: en vez de borrar la fila se pone en false y deja de listarse. Por defecto nace activo. */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    /** Lanes del pool. mappedBy: la clave foránea vive en Lane. Se borran en cascada con el pool. */
    @OneToMany(mappedBy = "pool")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Builder.Default
    private List<Lane> lanes = new ArrayList<>();
}

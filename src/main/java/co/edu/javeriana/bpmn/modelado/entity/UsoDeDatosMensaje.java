package co.edu.javeriana.bpmn.modelado.entity;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import co.edu.javeriana.bpmn.common.entity.EntidadDeEmpresa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * Tabla intermedia como entidad (HU-27): qué actividad del proceso usa los datos que recibe un
 * Message Catch. Como entidad propia admite borrado lógico y borrado en cascada en la base.
 */
@Entity
@Table(name = "uso_datos_mensaje")
@Getter
@Setter
@ToString(exclude = {"mensaje", "actividad"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsoDeDatosMensaje extends EntidadDeEmpresa {

    /** Identificador de la fila; la base de datos lo genera sola (autoincremental). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mensaje cuyos datos se usan. Se borra en cascada con el mensaje. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mensaje_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Mensaje mensaje;

    /** Actividad que usa los datos recibidos. Se borra en cascada con el nodo. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actividad_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private NodoFlujo actividad;

    /** Borrado lógico: en vez de borrar la fila se pone en false y deja de listarse. Por defecto nace activo. */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}

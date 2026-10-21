package co.edu.javeriana.bpmn.modelado.entity;

import co.edu.javeriana.bpmn.common.entity.EntidadDeEmpresa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Campo del contenido de un mensaje: nombre y tipo de dato (HU-25, HU-26 y HU-27). */
@Entity
@Table(name = "campo_mensaje")
@Getter
@Setter
@ToString(exclude = "mensaje")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampoMensaje extends EntidadDeEmpresa {

    /** Identificador de la fila; la base de datos lo genera sola (autoincremental). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mensaje al que pertenece este campo (muchos campos por mensaje). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mensaje_id", nullable = false)
    private Mensaje mensaje;

    /** Nombre del campo, por ejemplo el nombre de un dato que viaja en el mensaje. */
    @Column(length = 60, nullable = false)
    private String nombre;

    /** Tipo del dato. Se guarda como texto (EnumType.STRING) para que no dependa del orden del enum. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_dato", length = 20, nullable = false)
    private TipoDato tipoDato;

    /** Posición del campo dentro del mensaje, para mostrarlos siempre en el mismo orden. */
    @Column(nullable = false)
    private int orden;

    /** Borrado lógico: en vez de borrar la fila se pone en false y deja de listarse. Por defecto nace activo. */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}

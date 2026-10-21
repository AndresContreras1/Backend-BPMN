package co.edu.javeriana.bpmn.modelado.entity;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import co.edu.javeriana.bpmn.common.entity.EntidadDeEmpresa;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Arco o flujo de secuencia (HU-11): línea continua con punta sólida que fija el orden del trabajo
 * dentro de un pool. Nunca cruza pools: esa comunicación es un mensaje (HU-25).
 * La condición solo aplica cuando sale de un gateway exclusivo o inclusivo (HU-12).
 * Hereda de EntidadDeEmpresa, por lo que cada arco queda ligado a la empresa dueña.
 */
@Entity
@Table(name = "arco", indexes = {
        @Index(name = "idx_arco_proceso", columnList = "proceso_id"),
        @Index(name = "idx_arco_origen", columnList = "origen_id"),
        @Index(name = "idx_arco_destino", columnList = "destino_id")})
@NamedQuery(name = "Arco.listarActivosDelProceso",
        query = "select a from Arco a where a.proceso.id = :procesoId and a.activo = true order by a.id")
@Getter
@Setter
@ToString(exclude = {"proceso", "origen", "destino"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Arco extends EntidadDeEmpresa {

    /** Identificador de la fila; la base de datos lo genera sola (autoincremental). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Proceso al que pertenece el arco. LAZY: no se carga hasta que se use. ON DELETE CASCADE: si se borra el proceso, la base borra sus arcos. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Proceso proceso;

    /** Nodo de donde sale el arco. Se borra en cascada con el nodo. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origen_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private NodoFlujo origen;

    /** Nodo al que llega el arco. Se borra en cascada con el nodo. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destino_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private NodoFlujo destino;

    /** Texto opcional que se muestra sobre la línea (máximo 80 caracteres). */
    @Column(length = 80, nullable = true)
    private String etiqueta;

    /** Condición del camino; solo se usa si el arco sale de un gateway exclusivo o inclusivo (máximo 200 caracteres). */
    @Column(length = 200, nullable = true)
    private String condicion;

    /** Borrado lógico: en vez de borrar la fila se pone en false y deja de listarse. Por defecto nace activo. */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}

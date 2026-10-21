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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Proceso proceso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origen_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private NodoFlujo origen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destino_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private NodoFlujo destino;

    @Column(length = 80, nullable = true)
    private String etiqueta;

    @Column(length = 200, nullable = true)
    private String condicion;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}

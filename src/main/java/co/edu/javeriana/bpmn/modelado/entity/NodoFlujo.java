package co.edu.javeriana.bpmn.modelado.entity;

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
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Elemento del flujo: actividad (HU-08), gateway (HU-14) o evento. Un solo tipo de entidad
 * porque el arco (HU-11) une cualquiera de ellos. Siempre vive en un pool; la actividad,
 * además, en exactamente una lane, que define su rol responsable (HU-22).
 */
@Entity
@Table(name = "nodo_flujo", indexes = {
        @Index(name = "idx_nodo_proceso", columnList = "proceso_id"),
        @Index(name = "idx_nodo_lane", columnList = "lane_id")})
@NamedQuery(name = "NodoFlujo.listarActivosDelProceso",
        query = "select n from NodoFlujo n where n.proceso.id = :procesoId and n.activo = true order by n.id")
@Getter
@Setter
@ToString(exclude = {"proceso", "pool", "lane"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodoFlujo extends EntidadDeEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Proceso proceso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pool_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Pool pool;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lane_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Lane lane;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TipoNodo tipo;

    @Column(length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_actividad", length = 20)
    private TipoActividad tipoActividad;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_gateway", length = 20)
    private TipoGateway tipoGateway;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private VarianteEvento variante;

    /** Posición donde el usuario ubicó el elemento en el lienzo (HU-08). */
    @Column(name = "pos_x", nullable = false)
    private int posX;

    @Column(name = "pos_y", nullable = false)
    private int posY;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Version
    private Long version;

    public boolean esInicio() {
        return tipo == TipoNodo.EVENTO_INICIO || (tipo == TipoNodo.MESSAGE_CATCH && variante == VarianteEvento.INICIO);
    }

    public boolean esFin() {
        return tipo == TipoNodo.EVENTO_FIN || (tipo == TipoNodo.MESSAGE_THROW && variante == VarianteEvento.FIN);
    }

    public String etiqueta() {
        String texto = nombre == null || nombre.isBlank() ? tipo.name().toLowerCase().replace('_', ' ') : nombre;
        return "'" + texto + "'";
    }
}

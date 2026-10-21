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

    /** Identificador de la fila; la base de datos lo genera sola (autoincremental). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Proceso al que pertenece el nodo. Se borra en cascada con el proceso. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Proceso proceso;

    /** Pool en el que vive el nodo (siempre tiene uno). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pool_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Pool pool;

    /** Lane que contiene al nodo. Es opcional: solo las actividades la tienen. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lane_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Lane lane;

    /** Clase de elemento: actividad, gateway o alguno de los eventos. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TipoNodo tipo;

    /** Nombre visible del nodo; puede ser nulo (ver etiqueta()). */
    @Column(length = 120, nullable = true)
    private String nombre;

    /** Solo se llena si el nodo es una actividad. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_actividad", length = 20, nullable = true)
    private TipoActividad tipoActividad;

    /** Solo se llena si el nodo es un gateway. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_gateway", length = 20, nullable = true)
    private TipoGateway tipoGateway;

    /** Solo se llena en los eventos de mensaje: inicio, intermedio o fin. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = true)
    private VarianteEvento variante;

    /** Posición donde el usuario ubicó el elemento en el lienzo (HU-08): coordenada horizontal. */
    @Column(name = "pos_x", nullable = false)
    private int posX;

    /** Coordenada vertical de la posición en el lienzo. */
    @Column(name = "pos_y", nullable = false)
    private int posY;

    /** Borrado lógico: en vez de borrar la fila se pone en false y deja de listarse. Por defecto nace activo. */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    /** Control de concurrencia optimista (@Version): JPA lo aumenta en cada cambio y falla si dos usuarios editan el mismo nodo a la vez. */
    @Version
    private Long version;

    /** Dice si el nodo arranca el proceso: un evento de inicio o un Message Catch de variante INICIO. */
    public boolean esInicio() {
        return tipo == TipoNodo.EVENTO_INICIO || (tipo == TipoNodo.MESSAGE_CATCH && variante == VarianteEvento.INICIO);
    }

    /** Dice si el nodo termina el proceso: un evento de fin o un Message Throw de variante FIN. */
    public boolean esFin() {
        return tipo == TipoNodo.EVENTO_FIN || (tipo == TipoNodo.MESSAGE_THROW && variante == VarianteEvento.FIN);
    }

    /**
     * Texto entre comillas simples para usar en mensajes de error o advertencia.
     * Si no tiene nombre, usa el tipo en minúsculas y con espacios (por ejemplo evento inicio).
     */
    public String etiqueta() {
        String texto = nombre == null || nombre.isBlank() ? tipo.name().toLowerCase().replace('_', ' ') : nombre;
        return "'" + texto + "'";
    }
}

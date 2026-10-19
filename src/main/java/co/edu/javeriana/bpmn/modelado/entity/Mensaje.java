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
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Mensaje entre participantes, asociado al evento o tarea que lo envía o lo recibe.
 * ENVIO (HU-25 y HU-26): el flujo de mensaje, línea punteada con punta hueca, sale hacia otro pool.
 * RECEPCION (HU-27): el Message Catch que espera ese mensaje.
 * La clave de correlación (HU-28) dice a qué caso concreto corresponde el mensaje.
 * El sistema documenta y valida los mensajes; no los envía ni los recibe.
 */
@Entity
@Table(name = "mensaje", indexes = {
        @Index(name = "idx_mensaje_proceso", columnList = "proceso_id"),
        @Index(name = "idx_mensaje_pool_destino", columnList = "pool_destino_id")})
@NamedQuery(name = "Mensaje.listarActivosDelProceso",
        query = "select m from Mensaje m where m.proceso.id = :procesoId and m.activo = true order by m.id")
@Getter
@Setter
@ToString(exclude = {"proceso", "nodo", "poolDestino", "actividadManejoError", "campos", "usosDeDatos"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mensaje extends EntidadDeEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Proceso proceso;

    /** Evento de mensaje o tarea de envío que lo representa en el diagrama. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nodo_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private NodoFlujo nodo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private DireccionMensaje direccion;

    @Column(length = 120, nullable = false)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_destino_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Pool poolDestino;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_destino", length = 20, nullable = true)
    private TipoDestinoExterno tipoDestino;

    /** HU-26: en qué momento del proceso ocurre el envío. */
    @Column(length = 300, nullable = true)
    private String momento;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_fallo", length = 30, nullable = true)
    private PoliticaFallo politicaFallo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actividad_manejo_error_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private NodoFlujo actividadManejoError;

    /** HU-27: el mensaje llega de fuera del diagrama, sin un Message Throw que lo envíe. */
    @Column(name = "origen_externo", nullable = false)
    private boolean origenExterno;

    @Column(name = "clave_correlacion", length = 120, nullable = true)
    private String claveCorrelacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion_sin_caso", length = 30, nullable = true)
    private AccionSinCaso accionSinCaso;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @OneToMany(mappedBy = "mensaje")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Builder.Default
    private List<CampoMensaje> campos = new ArrayList<>();

    /** HU-27: actividades del proceso que usan los datos recibidos (tabla intermedia como entidad). */
    @OneToMany(mappedBy = "mensaje")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Builder.Default
    private List<UsoDeDatosMensaje> usosDeDatos = new ArrayList<>();
}

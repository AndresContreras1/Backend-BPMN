package co.edu.javeriana.bpmn.gestion.entity;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import co.edu.javeriana.bpmn.common.entity.EntidadDeEmpresa;
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
 * HU-23: el proceso (de la empresa dueña, empresaId) se comparte en solo lectura con otra empresa.
 * Es la única excepción al aislamiento entre empresas: se comparte el modelo, nunca los usuarios
 * ni el catálogo de roles.
 */
// Tabla "proceso_compartido" con índices por proceso y por empresa invitada (las dos búsquedas más comunes).
@Entity
@Table(name = "proceso_compartido", indexes = {
        @Index(name = "idx_compartido_proceso", columnList = "proceso_id"),
        @Index(name = "idx_compartido_invitada", columnList = "empresa_invitada_id")})
// Lombok genera getters, setters, constructores y builder; el toString excluye el proceso para no cargarlo.
@Getter
@Setter
@ToString(exclude = "proceso")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcesoCompartido extends EntidadDeEmpresa {

    /** Identificador del registro de compartición, autoincremental. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Proceso que se comparte. LAZY: solo se carga de la base cuando realmente se usa.
     * Con ON DELETE CASCADE, si el proceso se borra de la base se borra también esta fila.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Proceso proceso;

    /** Id de la empresa que recibe el acceso de solo lectura (la dueña está en empresaId, heredado). */
    @Column(name = "empresa_invitada_id", nullable = false)
    private Long empresaInvitadaId;

    /** false cuando se dejó de compartir (baja lógica, se conserva el registro). */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}

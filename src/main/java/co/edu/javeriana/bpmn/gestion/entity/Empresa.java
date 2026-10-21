package co.edu.javeriana.bpmn.gestion.entity;

import co.edu.javeriana.bpmn.common.entity.EntidadAuditada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Empresa registrada (HU-01): el espacio independiente del que cuelga todo dato del sistema.
 * En los diagramas aparece como el pool del participante dueño del proceso.
 */
// @Entity: esta clase se guarda como tabla "empresa". La restricción unique evita dos empresas con el mismo NIT.
@Entity
@Table(name = "empresa", uniqueConstraints = @UniqueConstraint(name = "uk_empresa_nit", columnNames = "nit"))
// Lombok genera getters, setters, toString, constructores y el patrón builder para no escribirlos a mano.
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Empresa extends EntidadAuditada {

    /** Identificador de la empresa; lo genera la base de datos con autoincremento (IDENTITY). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre comercial de la empresa (obligatorio, máximo 120 caracteres). */
    @Column(length = 120, nullable = false)
    private String nombre;

    /** NIT u otro identificador; único en todo el sistema. */
    @Column(length = 30, nullable = false)
    private String nit;

    /** Correo de contacto de la empresa; en la base la columna se llama correo_contacto. */
    @Column(name = "correo_contacto", length = 120, nullable = false)
    private String correoContacto;

    /** Indica si la empresa está habilitada; por defecto true al crearla con el builder. */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}

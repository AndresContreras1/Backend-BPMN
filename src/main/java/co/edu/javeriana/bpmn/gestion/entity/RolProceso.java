package co.edu.javeriana.bpmn.gestion.entity;

import co.edu.javeriana.bpmn.common.entity.EntidadDeEmpresa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Rol de proceso (HU-17): la función responsable de las actividades (Analista, Supervisor,
 * Auditor), no la persona. En el diagrama se dibuja como lane. No confundir con el rol de acceso.
 * Las lanes guardan la referencia al rol, no una copia del nombre: renombrarlo renombra todas (HU-18).
 */
// Tabla "rol_proceso" con índice por empresa (cada empresa tiene su propio catálogo de roles).
@Entity
@Table(name = "rol_proceso", indexes = @Index(name = "idx_rol_proceso_empresa", columnList = "empresa_id"))
// Consulta con nombre: cuenta las lanes activas (de pools y procesos activos) que usan el rol; sirve para
// impedir eliminar un rol que todavía está en uso.
@NamedQuery(name = "RolProceso.contarLanesActivasQueLoUsan",
        query = "select count(l) from Lane l where l.rol.id = :rolId and l.activo = true "
                + "and l.pool.activo = true and l.pool.proceso.activo = true")
// Consulta con nombre: lista (id y nombre) los procesos activos donde se usa el rol, sin repetidos y ordenados.
// "select distinct new ..." arma directamente objetos ProcesoResumen en lugar de entidades completas.
@NamedQuery(name = "RolProceso.procesosQueLoUsan",
        query = "select distinct new co.edu.javeriana.bpmn.gestion.dto.ProcesoResumen(p.id, p.nombre) "
                + "from Lane l join l.pool po join po.proceso p "
                + "where l.rol.id = :rolId and l.activo = true and po.activo = true and p.activo = true "
                + "order by p.nombre")
// Lombok genera getters, setters, toString, constructores y builder.
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolProceso extends EntidadDeEmpresa {

    /** Identificador del rol de proceso, autoincremental. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre del rol (por ejemplo Analista); es el que se muestra en las lanes. */
    @Column(length = 80, nullable = false)
    private String nombre;

    /** Explicación opcional de qué hace este rol. */
    @Column(length = 300, nullable = true)
    private String descripcion;

    /** false cuando el rol fue eliminado de forma lógica. */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}

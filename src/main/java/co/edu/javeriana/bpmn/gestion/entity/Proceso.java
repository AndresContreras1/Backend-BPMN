package co.edu.javeriana.bpmn.gestion.entity;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import co.edu.javeriana.bpmn.common.entity.EntidadDeEmpresa;
import co.edu.javeriana.bpmn.modelado.entity.Pool;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Proceso de la empresa (HU-04): el contenedor del diagrama. Pertenece a la empresa, no al
 * usuario que lo creó. La eliminación es lógica (activo = false) para conservar la trazabilidad.
 */
// Tabla "proceso" con índice por empresa, porque casi todas las consultas filtran por empresa_id.
@Entity
@Table(name = "proceso", indexes = @Index(name = "idx_proceso_empresa", columnList = "empresa_id"))
// Consulta con nombre: categorías distintas (sin repetir) de los procesos activos de la empresa, ordenadas.
@NamedQuery(name = "Proceso.listarCategoriasDeEmpresa",
        query = "select distinct p.categoria from Proceso p "
                + "where p.empresaId = :empresaId and p.activo = true order by p.categoria")
// Consulta con nombre: cuántos procesos activos de la empresa están en un estado dado (para el resumen).
@NamedQuery(name = "Proceso.contarPorEstado",
        query = "select count(p) from Proceso p "
                + "where p.empresaId = :empresaId and p.estado = :estado and p.activo = true")
// Lombok genera getters, setters, constructores y builder; el toString excluye la lista de pools para no cargarla.
@Getter
@Setter
@ToString(exclude = "pools")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Proceso extends EntidadDeEmpresa {

    /** Identificador del proceso, autoincremental. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre del proceso (obligatorio). */
    @Column(length = 120, nullable = false)
    private String nombre;

    /** Descripción opcional del proceso. */
    @Column(length = 500, nullable = true)
    private String descripcion;

    /** Categoría para agrupar y filtrar procesos (obligatoria). */
    @Column(length = 60, nullable = false)
    private String categoria;

    /** Estado del proceso (BORRADOR o PUBLICADO); se guarda como texto y empieza en BORRADOR. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    private EstadoProceso estado = EstadoProceso.BORRADOR;

    /** false cuando el proceso fue eliminado de forma lógica (sigue en la base, pero no se muestra). */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    /** Bloqueo optimista: dos ediciones simultáneas no se pisan (la segunda recibe 409). */
    @Version
    private Long version;

    /** Si el proceso se borrara físicamente, la base elimina en cascada sus pools y todo su contenido. */
    @OneToMany(mappedBy = "proceso")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Builder.Default
    private List<Pool> pools = new ArrayList<>();
}

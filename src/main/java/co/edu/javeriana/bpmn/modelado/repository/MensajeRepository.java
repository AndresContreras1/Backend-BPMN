package co.edu.javeriana.bpmn.modelado.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.modelado.entity.DireccionMensaje;
import co.edu.javeriana.bpmn.modelado.entity.Mensaje;

/** Acceso a datos de los mensajes entre participantes. */
@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    /**
     * Busca por id dentro de la empresa y solo entre los activos; así un usuario no ve datos de otra empresa ni borrados.
     * Spring Data arma la consulta a partir del nombre del método (findBy...And...).
     */
    Optional<Mensaje> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    /** Mensaje activo asociado a un nodo (como mucho uno, porque la relación nodo-mensaje es uno a uno). */
    Optional<Mensaje> findByNodoIdAndActivoTrue(Long nodoId);

    /** Mensajes activos de un proceso, ordenados por id. Named query Mensaje.listarActivosDelProceso (definida en la entidad Mensaje). */
    List<Mensaje> listarActivosDelProceso(@Param("procesoId") Long procesoId);

    /**
     * Mensajes del proceso con un nombre: el Message Throw y su Message Catch se corresponden por nombre.
     * IgnoreCase: no distingue mayúsculas de minúsculas.
     */
    List<Mensaje> findByProcesoIdAndNombreIgnoreCaseAndActivoTrue(Long procesoId, String nombre);

    /** Cuenta los mensajes activos que van dirigidos a un pool. */
    long countByPoolDestinoIdAndActivoTrue(Long poolDestinoId);

    /** Dice si hay un mensaje activo hacia ese pool, con esa dirección y con tipo de destino externo definido (NotNull). */
    boolean existsByPoolDestinoIdAndDireccionAndTipoDestinoNotNullAndActivoTrue(Long poolDestinoId,
                                                                               DireccionMensaje direccion);

    /** Mensajes activos que usan una actividad como manejo de error; sirve para saber si se puede borrar esa actividad. */
    List<Mensaje> findByActividadManejoErrorIdAndActivoTrue(Long actividadId);

}

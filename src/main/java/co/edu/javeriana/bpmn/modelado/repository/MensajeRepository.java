package co.edu.javeriana.bpmn.modelado.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.modelado.entity.DireccionMensaje;
import co.edu.javeriana.bpmn.modelado.entity.Mensaje;

@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    Optional<Mensaje> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    Optional<Mensaje> findByNodoIdAndActivoTrue(Long nodoId);

    /** Named query Mensaje.listarActivosDelProceso. */
    List<Mensaje> listarActivosDelProceso(@Param("procesoId") Long procesoId);

    /** Mensajes del proceso con un nombre: el Message Throw y su Message Catch se corresponden por nombre. */
    List<Mensaje> findByProcesoIdAndNombreIgnoreCaseAndActivoTrue(Long procesoId, String nombre);

    long countByPoolDestinoIdAndActivoTrue(Long poolDestinoId);

    boolean existsByPoolDestinoIdAndDireccionAndTipoDestinoNotNullAndActivoTrue(Long poolDestinoId,
                                                                               DireccionMensaje direccion);

    List<Mensaje> findByActividadManejoErrorIdAndActivoTrue(Long actividadId);

}

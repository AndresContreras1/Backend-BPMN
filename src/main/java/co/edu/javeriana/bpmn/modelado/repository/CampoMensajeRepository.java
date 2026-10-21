package co.edu.javeriana.bpmn.modelado.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.modelado.entity.CampoMensaje;

/** Acceso a datos de los campos del contenido de un mensaje. */
@Repository
public interface CampoMensajeRepository extends JpaRepository<CampoMensaje, Long> {

    /** Campos activos de un mensaje, en el orden en que el usuario los definió (columna orden de menor a mayor). */
    List<CampoMensaje> findByMensajeIdAndActivoTrueOrderByOrdenAsc(Long mensajeId);
}

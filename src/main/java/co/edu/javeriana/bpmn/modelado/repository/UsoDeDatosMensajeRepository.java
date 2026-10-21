package co.edu.javeriana.bpmn.modelado.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.modelado.entity.UsoDeDatosMensaje;

/** Acceso a datos de la relación entre un mensaje recibido y las actividades que usan sus datos (HU-27). */
@Repository
public interface UsoDeDatosMensajeRepository extends JpaRepository<UsoDeDatosMensaje, Long> {

    /** Usos activos de los datos de un mensaje (qué actividades lo usan). */
    List<UsoDeDatosMensaje> findByMensajeIdAndActivoTrue(Long mensajeId);

    /** Usos activos asociados a una actividad (qué mensajes usa). */
    List<UsoDeDatosMensaje> findByActividadIdAndActivoTrue(Long actividadId);
}

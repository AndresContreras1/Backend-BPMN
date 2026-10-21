package co.edu.javeriana.bpmn.modelado.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.modelado.entity.UsoDeDatosMensaje;

@Repository
public interface UsoDeDatosMensajeRepository extends JpaRepository<UsoDeDatosMensaje, Long> {

    List<UsoDeDatosMensaje> findByMensajeIdAndActivoTrue(Long mensajeId);

    List<UsoDeDatosMensaje> findByActividadIdAndActivoTrue(Long actividadId);
}

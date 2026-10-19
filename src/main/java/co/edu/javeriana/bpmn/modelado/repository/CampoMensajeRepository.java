package co.edu.javeriana.bpmn.modelado.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.modelado.entity.CampoMensaje;

@Repository
public interface CampoMensajeRepository extends JpaRepository<CampoMensaje, Long> {

    List<CampoMensaje> findByMensajeIdAndActivoTrueOrderByOrdenAsc(Long mensajeId);
}

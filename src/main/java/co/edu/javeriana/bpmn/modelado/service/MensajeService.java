package co.edu.javeriana.bpmn.modelado.service;

import java.util.List;

import co.edu.javeriana.bpmn.modelado.dto.CorrelacionRequest;
import co.edu.javeriana.bpmn.modelado.dto.EnvioExternoRequest;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.dto.MensajeActualizacionRequest;
import co.edu.javeriana.bpmn.modelado.dto.MensajeCatchRequest;
import co.edu.javeriana.bpmn.modelado.dto.MensajeGuardadoResponse;
import co.edu.javeriana.bpmn.modelado.dto.MensajeResponse;
import co.edu.javeriana.bpmn.modelado.dto.MensajeThrowRequest;

/**
 * Mensajes y colaboración entre pools: HU-25, HU-26, HU-27 y HU-28.
 * En BPMN los pools no se conectan con arcos; se comunican con mensajes
 * (un Throw que envia y un Catch que recibe).
 */
public interface MensajeService {

    /** Lista los mensajes activos de un proceso. */
    List<MensajeResponse> listar(Long procesoId, Long empresaId);

    /** Devuelve un mensaje por id junto con las advertencias del diagrama. */
    MensajeGuardadoResponse obtener(Long id, Long empresaId);

    /** HU-25: Message Throw hacia otro pool del diagrama. */
    MensajeGuardadoResponse crearThrow(Long procesoId, MensajeThrowRequest request, Long empresaId);

    /** HU-26: notificación a un sistema externo de caja negra. */
    MensajeGuardadoResponse crearEnvioExterno(Long procesoId, EnvioExternoRequest request, Long empresaId);

    /** HU-27: Message Catch de inicio o intermedio. */
    MensajeGuardadoResponse crearCatch(Long procesoId, MensajeCatchRequest request, Long empresaId);

    /** Modifica un mensaje ya creado (nombre, extremos, etc.). */
    MensajeGuardadoResponse actualizar(Long id, MensajeActualizacionRequest request, Long empresaId);

    /** HU-28: clave de correlación, coherente entre el Throw y el Catch que se corresponden. */
    MensajeGuardadoResponse definirCorrelacion(Long id, CorrelacionRequest request, Long empresaId);

    /** Calcula el impacto de eliminar el mensaje (problemas nuevos en el diagrama). */
    ImpactoResponse impacto(Long id, Long empresaId);

    /** Elimina el mensaje (eliminacion logica). */
    void eliminar(Long id, Long empresaId);
}

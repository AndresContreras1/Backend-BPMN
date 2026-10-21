package co.edu.javeriana.bpmn.modelado.service;

import java.util.List;

import co.edu.javeriana.bpmn.modelado.dto.LaneRequest;
import co.edu.javeriana.bpmn.modelado.dto.LaneResponse;
import co.edu.javeriana.bpmn.modelado.dto.ReordenLanesRequest;
import co.edu.javeriana.bpmn.modelado.entity.Lane;

/**
 * Lanes de un pool (HU-22): crear, renombrar (cambiar su rol), reordenar y eliminar.
 * Una lane es un carril del pool que representa a un rol de proceso.
 */
public interface LaneService {

    /** Lista las lanes activas de un pool, en su orden. */
    List<LaneResponse> listar(Long poolId, Long empresaId);

    /** Crea una lane nueva en el pool. */
    LaneResponse crear(Long poolId, LaneRequest request, Long empresaId);

    /** Cambia el nombre o el rol de la lane. */
    LaneResponse actualizar(Long id, LaneRequest request, Long empresaId);

    /** Cambia el orden de las lanes del pool segun la lista recibida. */
    List<LaneResponse> reordenar(Long poolId, ReordenLanesRequest request, Long empresaId);

    /** Elimina la lane (eliminacion logica). */
    void eliminar(Long id, Long empresaId);

    /** Puerta de escritura: lane activa de un pool y un proceso activos de la empresa. */
    Lane obtenerDeEmpresa(Long id, Long empresaId);
}

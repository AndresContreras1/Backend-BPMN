package co.edu.javeriana.bpmn.modelado.service.impl;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;
import co.edu.javeriana.bpmn.gestion.service.HistorialService;
import co.edu.javeriana.bpmn.gestion.service.ProcesoService;
import co.edu.javeriana.bpmn.modelado.dto.Advertencia;
import co.edu.javeriana.bpmn.modelado.dto.ArcoRequest;
import co.edu.javeriana.bpmn.modelado.dto.ArcoResponse;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.entity.Arco;
import co.edu.javeriana.bpmn.modelado.entity.NodoFlujo;
import co.edu.javeriana.bpmn.modelado.entity.TipoGateway;
import co.edu.javeriana.bpmn.modelado.entity.TipoNodo;
import co.edu.javeriana.bpmn.modelado.repository.ArcoRepository;
import co.edu.javeriana.bpmn.modelado.service.ArcoService;
import co.edu.javeriana.bpmn.modelado.service.NodoFlujoService;
import co.edu.javeriana.bpmn.modelado.service.ValidadorDeProcesoService;
import lombok.RequiredArgsConstructor;

/**
 * Casos de uso de los arcos o flechas del diagrama (HU-11 crear, HU-12 editar, HU-13 eliminar).
 * Un arco une dos nodos del MISMO pool; entre pools solo hay mensajes. Aqui estan las reglas BPMN
 * de los arcos y el filtro por empresa.
 * <p>@Service registra el bean; @RequiredArgsConstructor (Lombok) genera el constructor.
 */
@Service
@RequiredArgsConstructor
public class ArcoServiceImpl implements ArcoService {

    /** Un arco puede unir actividades, gateways y eventos (HU-11). */
    private static final Set<TipoNodo> NODOS = EnumSet.allOf(TipoNodo.class);

    /** Guarda y consulta arcos en base de datos. */
    private final ArcoRepository arcoRepository;
    /** Busca los nodos extremos del arco con filtro de empresa. */
    private final NodoFlujoService nodoFlujoService;
    /** Busca el proceso y comprueba que sea de la empresa. */
    private final ProcesoService procesoService;
    /** Registra creaciones, ediciones y eliminaciones en el historial. */
    private final HistorialService historialService;
    /** Revisa la coherencia del diagrama tras cada cambio. */
    private final ValidadorDeProcesoService validador;

    /** Lista los arcos activos del proceso. Solo lectura; el proceso debe ser de la empresa. */
    @Override
    @Transactional(readOnly = true)
    public List<ArcoResponse> listar(Long procesoId, Long empresaId) {
        Proceso proceso = procesoService.obtenerParaLectura(procesoId, empresaId);
        return arcoRepository.listarActivosDelProceso(proceso.getId()).stream().map(ArcoResponse::de).toList();
    }

    /** Devuelve un arco de la empresa (404 si no existe o es de otra empresa). */
    @Override
    @Transactional(readOnly = true)
    public ArcoResponse obtener(Long id, Long empresaId) {
        return ArcoResponse.de(obtenerDeEmpresa(id, empresaId));
    }

    /**
     * HU-11: crea un arco entre dos nodos.
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: se guarda todo o nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public ArcoResponse crear(ArcoRequest request, Long empresaId) {
        // Paso 1: resolver proceso, origen y destino. Cada uno pasa por su puerta con filtro de empresa,
        // asi un id de otra empresa responde 404 y nunca se puede enlazar con datos ajenos.
        // Cada id del cuerpo se resuelve por su propia puerta: lo ajeno es 404
        Proceso proceso = procesoService.obtenerDeEmpresa(request.procesoId(), empresaId);
        NodoFlujo origen = nodoFlujoService.obtenerDeEmpresa(request.origenId(), empresaId, NODOS, "el nodo");
        NodoFlujo destino = nodoFlujoService.obtenerDeEmpresa(request.destinoId(), empresaId, NODOS, "el nodo");
        // Paso 2: reglas BPMN de los extremos (mismo proceso, mismo pool, inicio y fin).
        validarExtremos(proceso, origen, destino);
        // Paso 3: no se permiten arcos duplicados entre el mismo origen y destino.
        if (arcoRepository.existsByOrigenIdAndDestinoIdAndActivoTrue(origen.getId(), destino.getId())) {
            throw new ReglaDeNegocioException("Ya existe un arco de " + origen.etiqueta() + " a " + destino.etiqueta());
        }

        // Paso 4: construir el arco; la condicion se valida segun el tipo de nodo de origen.
        Arco arco = Arco.builder()
                .proceso(proceso)
                .origen(origen)
                .destino(destino)
                .etiqueta(textoONulo(request.etiqueta()))
                .condicion(validarCondicion(origen, request.condicion()))
                .build();
        // El arco queda ligado a la empresa para que el filtro por empresa lo encuentre despues.
        arco.setEmpresaId(empresaId);
        arco = arcoRepository.save(arco);

        // Paso 5: historial del cambio.
        historialService.registrar(proceso, TipoCambio.CREACION, ElementoHistorial.ARCO, arco.getId(),
                "Arco " + origen.etiqueta() + " → " + destino.etiqueta() + " creado"
                        + (arco.getCondicion() == null ? "" : " con condición '" + arco.getCondicion() + "'"));
        // Paso 6: si el proceso esta PUBLICADO y el cambio deja errores, 409 y se revierte.
        validador.exigirCoherenciaSiPublicado(proceso);
        return ArcoResponse.de(arco);
    }

    /**
     * HU-12: edita los extremos, la etiqueta o la condicion de un arco.
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: cambios atomicos.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public ArcoResponse actualizar(Long id, ArcoRequest request, Long empresaId) {
        // Paso 1: buscar el arco con filtro de empresa.
        Arco arco = obtenerDeEmpresa(id, empresaId);
        Proceso proceso = arco.getProceso();
        // Paso 2: el arco no puede cambiarse de proceso; el procesoId del cuerpo debe ser el suyo.
        if (!proceso.getId().equals(request.procesoId())) {
            throw new ReglaDeNegocioException("El arco no pertenece al proceso " + request.procesoId());
        }
        NodoFlujo origen = nodoFlujoService.obtenerDeEmpresa(request.origenId(), empresaId, NODOS, "el nodo");
        NodoFlujo destino = nodoFlujoService.obtenerDeEmpresa(request.destinoId(), empresaId, NODOS, "el nodo");
        // Paso 3: resolver los nuevos extremos con filtro de empresa.
        // HU-12: las mismas validaciones de la creación
        validarExtremos(proceso, origen, destino);
        if (arcoRepository.existsByOrigenIdAndDestinoIdAndActivoTrueAndIdNot(origen.getId(), destino.getId(), id)) {
            throw new ReglaDeNegocioException("Ya existe un arco de " + origen.etiqueta() + " a " + destino.etiqueta());
        }
        // Paso 4: validar la condicion con el (posible) nuevo origen.
        String condicion = validarCondicion(origen, request.condicion());

        // Paso 5: texto 'antes' y 'despues' para el historial; se arma antes de modificar el arco.
        String antes = arco.getOrigen().etiqueta() + " → " + arco.getDestino().etiqueta()
                + (arco.getCondicion() == null ? "" : " [" + arco.getCondicion() + "]");
        // Paso 6: aplicar los cambios (JPA los guarda al terminar la transaccion, no hace falta save).
        arco.setOrigen(origen);
        arco.setDestino(destino);
        arco.setEtiqueta(textoONulo(request.etiqueta()));
        arco.setCondicion(condicion);
        String despues = origen.etiqueta() + " → " + destino.etiqueta() + (condicion == null ? "" : " [" + condicion + "]");

        // Paso 7: solo se registra en el historial si el arco realmente cambio.
        if (!Objects.equals(antes, despues)) {
            historialService.registrar(proceso, TipoCambio.EDICION, ElementoHistorial.ARCO, id,
                    "Arco: " + antes + " ⇒ " + despues);
        }
        // Paso 8: coherencia si el proceso esta PUBLICADO.
        validador.exigirCoherenciaSiPublicado(proceso);
        return ArcoResponse.de(arco);
    }

    /** Dice que arco se iria y que problemas nuevos aparecerian si se elimina. Solo lectura. */
    @Override
    @Transactional(readOnly = true)
    public ImpactoResponse impacto(Long id, Long empresaId) {
        Arco arco = obtenerDeEmpresa(id, empresaId);
        return new ImpactoResponse(List.of(id), nuevas(arco));
    }

    /**
     * HU-13: elimina un arco (baja logica).
     * <p>@PreAuthorize: solo el ADMINISTRADOR puede eliminar. @Transactional: todo junto o nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public void eliminar(Long id, Long empresaId) {
        Arco arco = obtenerDeEmpresa(id, empresaId);
        // Se calculan los problemas nuevos antes de apagar el arco.
        List<Advertencia> avisos = nuevas(arco);
        // Baja logica: el arco se marca inactivo, no se borra de la base de datos.
        arco.setActivo(false);
        historialService.registrar(arco.getProceso(), TipoCambio.ELIMINACION, ElementoHistorial.ARCO, id,
                "Arco " + arco.getOrigen().etiqueta() + " → " + arco.getDestino().etiqueta() + " eliminado",
                avisos.stream().map(Advertencia::mensaje).toList());
        // Si el proceso esta PUBLICADO y queda con errores, 409 y se revierte la baja.
        validador.exigirCoherenciaSiPublicado(arco.getProceso());
    }

    /**
     * Puerta de escritura/lectura de arcos: lo busca por id, empresa y activo, y exige que su proceso
     * siga activo. Si no cumple, 404.
     */
    private Arco obtenerDeEmpresa(Long id, Long empresaId) {
        return arcoRepository.findByIdAndEmpresaIdAndActivoTrue(id, empresaId)
                .filter(a -> a.getProceso().isActivo())
                .orElseThrow(() -> new NotFoundException("No existe el arco " + id));
    }

    /** HU-13: elementos que quedarían sin camino de entrada o de salida al quitar el arco. */
    // Compara el diagrama antes y despues de quitar el arco; lo nuevo es lo que causaria la eliminacion.
    private List<Advertencia> nuevas(Arco arco) {
        List<String> antes = validador.revisar(arco.getProceso()).stream().map(Advertencia::mensaje).toList();
        return validador.revisarSin(arco.getProceso(), Set.of(), Set.of(arco.getId())).stream()
                .filter(a -> !antes.contains(a.mensaje()))
                .toList();
    }

    /**
     * Reglas BPMN de un arco. Cada if lanza 409 (ReglaDeNegocioException) si se rompe una regla.
     */
    private static void validarExtremos(Proceso proceso, NodoFlujo origen, NodoFlujo destino) {
        // Regla 1: ambos nodos deben ser del proceso del arco.
        if (!origen.getProceso().getId().equals(proceso.getId()) || !destino.getProceso().getId().equals(proceso.getId())) {
            throw new ReglaDeNegocioException("El arco debe unir nodos del mismo proceso");
        }
        // Regla 2: un nodo no se une consigo mismo.
        if (origen.getId().equals(destino.getId())) {
            throw new ReglaDeNegocioException("El origen y el destino no pueden ser el mismo elemento");
        }
        // Regla 3 (clave de BPMN): un arco no cruza pools. Entre pools la comunicacion es un mensaje.
        if (!origen.getPool().getId().equals(destino.getPool().getId())) {
            throw new ReglaDeNegocioException("Un arco no cruza de un pool a otro: esa comunicación se modela como mensaje");
        }
        // Regla 4: el evento de inicio solo arranca el flujo, nada puede llegar a el.
        if (destino.esInicio()) {
            throw new ReglaDeNegocioException("Un evento de inicio no puede tener arcos entrantes");
        }
        // Regla 5: el evento de fin termina el flujo, de el no puede salir nada.
        if (origen.esFin()) {
            throw new ReglaDeNegocioException("Un evento de fin no puede tener arcos salientes");
        }
    }

    /** HU-12: la condición solo existe en los arcos que salen de un gateway exclusivo o inclusivo. */
    /** Metodo auxiliar: devuelve la condicion ya limpia o null, y lanza 409 si no corresponde ponerla. */
    private static String validarCondicion(NodoFlujo origen, String condicion) {
        // Sin condicion no hay nada que validar.
        String texto = textoONulo(condicion);
        if (texto == null) {
            return null;
        }
        // Solo los arcos que salen de un gateway pueden llevar condicion (las ramas de la decision).
        if (origen.getTipo() != TipoNodo.GATEWAY) {
            throw new ReglaDeNegocioException("Solo los arcos que salen de un gateway llevan condición");
        }
        // En un gateway paralelo se siguen todas las ramas, por eso no hay condicion que evaluar.
        if (origen.getTipoGateway() == TipoGateway.PARALELO) {
            throw new ReglaDeNegocioException("Los arcos de un gateway paralelo no llevan condición: se siguen todos los caminos");
        }
        return texto;
    }

    /** Metodo auxiliar: texto vacio o solo espacios se guarda como null; si no, se recorta. */
    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}

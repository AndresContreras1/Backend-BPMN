package co.edu.javeriana.bpmn.gestion.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.dto.PageResponse;
import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.common.util.Paginacion;
import co.edu.javeriana.bpmn.gestion.dto.ComparticionResponse;
import co.edu.javeriana.bpmn.gestion.dto.CompartirRequest;
import co.edu.javeriana.bpmn.gestion.dto.FiltroProceso;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoCompartidoResponse;
import co.edu.javeriana.bpmn.gestion.dto.HistorialResponse;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoActualizacionRequest;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoRequest;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoResponse;
import co.edu.javeriana.bpmn.gestion.dto.ResumenProcesosResponse;
import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.Empresa;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.ProcesoCompartido;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;
import co.edu.javeriana.bpmn.gestion.repository.EmpresaRepository;
import co.edu.javeriana.bpmn.gestion.repository.ProcesoCompartidoRepository;
import co.edu.javeriana.bpmn.gestion.repository.ProcesoRepository;
import co.edu.javeriana.bpmn.gestion.service.EmpresaService;
import co.edu.javeriana.bpmn.gestion.service.HistorialService;
import co.edu.javeriana.bpmn.gestion.service.ProcesoService;
import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoPool;
import co.edu.javeriana.bpmn.modelado.repository.PoolRepository;
import co.edu.javeriana.bpmn.modelado.service.ValidadorDeProcesoService;
import co.edu.javeriana.bpmn.gestion.entity.EstadoProceso;
import lombok.RequiredArgsConstructor;

/**
 * Implementación de la gestión de procesos: crear, editar, eliminar (lógicamente), listar, ver el
 * historial y compartir con otras empresas. Siempre se trabaja dentro de la empresa del usuario.
 */
// @Service: bean de Spring. @RequiredArgsConstructor: constructor con los campos final para la inyección.
@Service
@RequiredArgsConstructor
public class ProcesoServiceImpl implements ProcesoService {

    /** Campos por los que se permite ordenar la lista de procesos. */
    private static final Set<String> ORDEN_PERMITIDO =
            Set.of("nombre", "categoria", "estado", "creadoEn", "modificadoEn");
    /** Campos por los que se permite ordenar el historial. */
    private static final Set<String> ORDEN_HISTORIAL = Set.of("fecha", "id", "tipo", "elemento");

    /** Acceso a la tabla de procesos. */
    private final ProcesoRepository procesoRepository;
    /** Acceso a la tabla de procesos compartidos. */
    private final ProcesoCompartidoRepository compartidoRepository;
    /** Acceso a la tabla de empresas (para buscar las invitadas por NIT). */
    private final EmpresaRepository empresaRepository;
    /** Acceso a los pools del módulo de modelado (se crea uno al crear el proceso). */
    private final PoolRepository poolRepository;
    /** Servicio de empresas, para traer el nombre de la empresa propietaria. */
    private final EmpresaService empresaService;
    /** Servicio que guarda el historial de cambios. */
    private final HistorialService historialService;
    /** Revisa que el diagrama sea coherente antes de publicar el proceso. */
    private final ValidadorDeProcesoService validador;

    /** HU-07: lista paginada de procesos de la empresa, con los filtros de búsqueda. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProcesoResponse> listar(Long empresaId, FiltroProceso filtro, Pageable pageable) {
        // Valida el campo de orden pedido; luego busca con los filtros (texto nulo se vuelve "" = sin filtro).
        Pageable orden = Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        return PageResponse.de(procesoRepository.buscar(empresaId, textoOVacio(filtro.nombre()), filtro.estado(),
                        textoOVacio(filtro.categoria()), filtro.activo(), orden),
                p -> ProcesoResponse.de(p, empresaId));
    }

    /** Categorías en uso por los procesos activos de la empresa (para el filtro de la lista). */
    @Override
    @Transactional(readOnly = true)
    public List<String> categorias(Long empresaId) {
        return procesoRepository.listarCategoriasDeEmpresa(empresaId);
    }

    /** HU-07: contadores para la página de inicio. */
    @Override
    @Transactional(readOnly = true)
    public ResumenProcesosResponse resumen(Long empresaId) {
        // Named query Proceso.contarPorEstado: solo cuenta los activos
        long borradores = procesoRepository.contarPorEstado(empresaId, EstadoProceso.BORRADOR);
        long publicados = procesoRepository.contarPorEstado(empresaId, EstadoProceso.PUBLICADO);
        // total = borradores + publicados; eliminados = inactivos; compartidos = los que otras empresas le dieron.
        return new ResumenProcesosResponse(borradores + publicados, borradores, publicados,
                procesoRepository.countByEmpresaIdAndActivoFalse(empresaId),
                procesoRepository.contarCompartidosCon(empresaId));
    }

    /** HU-07: ficha de un proceso, propio o compartido conmigo (puerta de lectura). */
    @Override
    @Transactional(readOnly = true)
    public ProcesoResponse obtener(Long id, Long empresaId) {
        return ProcesoResponse.de(obtenerParaLectura(id, empresaId), empresaId);
    }

    /**
     * HU-04: crea el proceso en BORRADOR y su pool inicial. Solo ADMINISTRADOR o EDITOR
     * (@PreAuthorize); un LECTOR recibe 403.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public ProcesoResponse crear(ProcesoRequest request, Long empresaId) {
        // Paso 1: el nombre no puede repetirse entre los procesos activos de la empresa.
        String nombre = request.nombre().trim();
        if (procesoRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrue(nombre, empresaId)) {
            throw new ReglaDeNegocioException("Ya existe un proceso llamado '" + nombre + "' en la empresa");
        }

        // Paso 2: arma y guarda el proceso (estado y activo toman sus valores por defecto).
        Proceso proceso = Proceso.builder()
                .nombre(nombre)
                .descripcion(textoONulo(request.descripcion()))
                .categoria(request.categoria().trim())
                .build();
        proceso.setEmpresaId(empresaId);   // lo pone el servidor, no el cliente
        proceso = procesoRepository.save(proceso);

        // HU-04 y HU-21: el proceso queda listo para modelar con el pool de la empresa propietaria
        Pool poolEmpresa = Pool.builder()
                .proceso(proceso)
                .nombre(empresaService.obtenerEntidad(empresaId).getNombre())
                .tipo(TipoPool.PROPIETARIO)
                .cajaNegra(false)
                .orden(0)
                .build();
        poolEmpresa.setEmpresaId(empresaId);
        poolRepository.save(poolEmpresa);

        // Paso 3: deja constancia de la creación en el historial y devuelve el proceso.
        historialService.registrar(proceso, TipoCambio.CREACION, ElementoHistorial.PROCESO, proceso.getId(),
                "Proceso creado en estado BORRADOR con el pool de la empresa '" + poolEmpresa.getNombre() + "'");
        return ProcesoResponse.de(proceso, empresaId);
    }

    /**
     * HU-05: edita los datos del proceso (nombre, descripción, categoría y estado). Solo ADMINISTRADOR
     * o EDITOR. Detecta ediciones simultáneas con la versión y registra en el historial qué cambió.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public ProcesoResponse actualizar(Long id, ProcesoActualizacionRequest request, Long empresaId) {
        // Paso 1: carga el proceso activo de la empresa (404 si no existe o es de otra empresa).
        Proceso proceso = obtenerDeEmpresa(id, empresaId);
        // Paso 2: bloqueo optimista; si el cliente envía una versión distinta a la actual, otro editó antes (409).
        if (request.version() != null && !request.version().equals(proceso.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Proceso.class, id);
        }

        // Paso 3: el nuevo nombre no puede coincidir con el de otro proceso activo (IdNot excluye al mismo proceso).
        String nombre = request.nombre().trim();
        if (procesoRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrueAndIdNot(nombre, empresaId, id)) {
            throw new ReglaDeNegocioException("Ya existe un proceso llamado '" + nombre + "' en la empresa");
        }

        // Paso 4: compara valor viejo y nuevo de cada campo y anota solo los que cambiaron.
        List<String> cambios = new ArrayList<>();
        anotar(cambios, "nombre", proceso.getNombre(), nombre);
        anotar(cambios, "descripción", proceso.getDescripcion(), textoONulo(request.descripcion()));
        anotar(cambios, "categoría", proceso.getCategoria(), request.categoria().trim());
        boolean cambiaEstado = proceso.getEstado() != request.estado();
        anotar(cambios, "estado", proceso.getEstado(), request.estado());

        // HU-04: en borrador el diagrama puede estar incompleto; publicado debe ser coherente
        if (request.estado() == EstadoProceso.PUBLICADO) {
            validador.exigirCoherenciaParaPublicar(proceso);
        }
        // Paso 5: aplica los cambios sobre la entidad (la transacción los guarda al terminar).
        proceso.setNombre(nombre);
        proceso.setDescripcion(textoONulo(request.descripcion()));
        proceso.setCategoria(request.categoria().trim());
        proceso.setEstado(request.estado());

        // Paso 6: si algo cambió, lo registra en el historial (CAMBIO_ESTADO si cambió el estado, si no EDICION).
        if (!cambios.isEmpty()) {
            historialService.registrar(proceso, cambiaEstado ? TipoCambio.CAMBIO_ESTADO : TipoCambio.EDICION,
                    ElementoHistorial.PROCESO, id, String.join("; ", cambios));
        }
        procesoRepository.flush();   // la versión que vuelve al cliente ya es la nueva
        return ProcesoResponse.de(proceso, empresaId);
    }

    /** HU-06: eliminación lógica del proceso. Solo ADMINISTRADOR (@PreAuthorize). */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public void eliminar(Long id, Long empresaId) {
        Proceso proceso = obtenerDeEmpresa(id, empresaId);   // 404 si no es suyo o ya está inactivo
        proceso.setActivo(false);                             // el "borrado": el diagrama se conserva
        historialService.registrar(proceso, TipoCambio.ELIMINACION, ElementoHistorial.PROCESO, id,
                "Proceso eliminado (inactivo); su diagrama se conserva para auditoría");
    }

    /** HU-07: historial paginado de un proceso propio (las empresas invitadas no lo ven). */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<HistorialResponse> historial(Long id, Long empresaId, Pageable pageable) {
        Pageable orden = Paginacion.validarOrden(pageable, ORDEN_HISTORIAL);
        Proceso proceso = obtenerParaLecturaPropia(id, empresaId);
        return historialService.listarPorProceso(proceso.getId(), proceso.getEmpresaId(), orden);
    }

    /**
     * HU-23: comparte el proceso en solo lectura con las empresas indicadas por NIT, o deja de
     * compartirlo si compartido = false. Solo ADMINISTRADOR. La lista enviada reemplaza a la anterior.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public ComparticionResponse compartir(Long id, CompartirRequest request, Long empresaId) {
        Proceso proceso = obtenerDeEmpresa(id, empresaId);   // solo la empresa propietaria (404 a las invitadas)
        // Comparticiones que están activas ahora mismo.
        List<ProcesoCompartido> actuales = compartidoRepository.findByProcesoIdAndActivoTrue(id);

        // Paso 1: si se quiere compartir, valida los NITs recibidos y busca las empresas invitadas.
        List<Empresa> invitadas = List.of();
        if (Boolean.TRUE.equals(request.compartido())) {
            // LinkedHashSet quita NITs repetidos conservando el orden.
            Set<String> nits = new LinkedHashSet<>(request.nitsEmpresas() == null ? List.of() : request.nitsEmpresas());
            if (nits.isEmpty()) {
                throw new ReglaDeNegocioException("Indique al menos el NIT de una empresa con la que se comparte el proceso");
            }
            invitadas = empresaRepository.findByNitInAndActivoTrue(List.copyOf(nits));
            // Detecta los NITs que no corresponden a ninguna empresa activa.
            Set<String> encontrados = invitadas.stream().map(Empresa::getNit).collect(Collectors.toSet());
            List<String> faltantes = nits.stream().filter(n -> !encontrados.contains(n)).toList();
            if (!faltantes.isEmpty()) {
                throw new NotFoundException("No existe una empresa registrada con el NIT " + String.join(", ", faltantes));
            }
            // No tiene sentido compartir el proceso con la propia empresa dueña.
            if (invitadas.stream().anyMatch(e -> e.getId().equals(empresaId))) {
                throw new ReglaDeNegocioException("Un proceso no se comparte con la misma empresa que lo creó");
            }
        }

        // Paso 2: de las comparticiones actuales, desactiva las que ya no están en la lista nueva
        // y recuerda cuáles se mantienen para no duplicarlas.
        Set<Long> nuevas = invitadas.stream().map(Empresa::getId).collect(Collectors.toSet());
        Set<Long> yaCompartidas = new HashSet<>();
        for (ProcesoCompartido c : actuales) {
            if (nuevas.contains(c.getEmpresaInvitadaId())) {
                yaCompartidas.add(c.getEmpresaInvitadaId());
            } else {
                c.setActivo(false);
            }
        }
        // Paso 3: crea la compartición solo para las empresas invitadas que aún no la tenían.
        for (Empresa empresa : invitadas) {
            if (!yaCompartidas.contains(empresa.getId())) {
                ProcesoCompartido compartido = ProcesoCompartido.builder().proceso(proceso).empresaInvitadaId(empresa.getId()).build();
                compartido.setEmpresaId(empresaId);
                compartidoRepository.save(compartido);
            }
        }

        // Paso 4: deja constancia en el historial y devuelve el estado actual de la compartición.
        String detalle = invitadas.isEmpty()
                ? "El proceso dejó de compartirse"
                : "Proceso compartido en solo lectura con: " + invitadas.stream()
                        .map(e -> e.getNombre() + " (" + e.getNit() + ")").collect(Collectors.joining(", "));
        historialService.registrar(proceso, TipoCambio.COMPARTICION, ElementoHistorial.PROCESO, id, detalle);
        return respuestaComparticion(invitadas);
    }

    /** HU-23: consulta con qué empresas está compartido el proceso (solo para la empresa dueña). */
    @Override
    @Transactional(readOnly = true)
    public ComparticionResponse comparticion(Long id, Long empresaId) {
        Proceso proceso = obtenerParaLecturaPropia(id, empresaId);
        // Ids de las empresas invitadas con compartición activa y luego sus datos.
        List<Long> ids = compartidoRepository.findByProcesoIdAndActivoTrue(proceso.getId()).stream()
                .map(ProcesoCompartido::getEmpresaInvitadaId).toList();
        return respuestaComparticion(empresaRepository.findAllById(ids));
    }

    /** HU-23: página de procesos que otras empresas compartieron con esta, con el nombre de la dueña. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProcesoCompartidoResponse> compartidosConmigo(Long empresaId, Pageable pageable) {
        Pageable orden = Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        var pagina = procesoRepository.buscarCompartidosCon(empresaId, orden);
        // Una sola consulta trae los nombres de todas las empresas dueñas de la página (id -> nombre).
        Map<Long, String> propietarias = empresaRepository.findAllById(
                        pagina.getContent().stream().map(Proceso::getEmpresaId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Empresa::getId, Empresa::getNombre, (a, c) -> a));
        return PageResponse.de(pagina, p -> new ProcesoCompartidoResponse(ProcesoResponse.de(p, empresaId),
                propietarias.getOrDefault(p.getEmpresaId(), "")));
    }

    /** Arma la respuesta de compartición: "compartido" es true si hay al menos una empresa. */
    private static ComparticionResponse respuestaComparticion(List<Empresa> empresas) {
        return new ComparticionResponse(!empresas.isEmpty(), empresas.stream()
                .map(e -> new ComparticionResponse.EmpresaInvitada(e.getNombre(), e.getNit())).toList());
    }

    /** Lectura solo de lo propio: el historial y la compartición no se muestran a las invitadas. */
    private Proceso obtenerParaLecturaPropia(Long id, Long empresaId) {
        return procesoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new NotFoundException("No existe el proceso " + id));
    }

    /** Puerta de escritura: proceso activo y de la empresa; cualquier otro caso es 404. */
    @Override
    @Transactional(readOnly = true)
    public Proceso obtenerDeEmpresa(Long id, Long empresaId) {
        return procesoRepository.findByIdAndEmpresaIdAndActivoTrue(id, empresaId)
                .orElseThrow(() -> new NotFoundException("No existe el proceso " + id));
    }

    /** Puerta de lectura: proceso propio (activo o no) o compartido conmigo en solo lectura. */
    @Override
    @Transactional(readOnly = true)
    public Proceso obtenerParaLectura(Long id, Long empresaId) {
        return procesoRepository.findLegibleByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new NotFoundException("No existe el proceso " + id));
    }

    /** Si el valor cambió, agrega a la lista un texto "campo: 'antes' → 'después'" para el historial. */
    private static void anotar(List<String> cambios, String campo, Object antes, Object despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.add(campo + ": '" + (antes == null ? "" : antes) + "' → '" + (despues == null ? "" : despues) + "'");
        }
    }

    /** Devuelve el texto sin espacios en los bordes, o "" si es nulo (para filtros que no filtran). */
    private static String textoOVacio(String texto) {
        return texto == null ? "" : texto.trim();
    }

    /** Devuelve el texto sin espacios en los bordes, o null si es nulo o está en blanco. */
    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}

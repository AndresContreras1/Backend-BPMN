package co.edu.javeriana.bpmn.config;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.gestion.dto.CompartirRequest;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoActualizacionRequest;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoRequest;
import co.edu.javeriana.bpmn.gestion.dto.RolProcesoRequest;
import co.edu.javeriana.bpmn.gestion.entity.Empresa;
import co.edu.javeriana.bpmn.gestion.entity.EstadoProceso;
import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;
import co.edu.javeriana.bpmn.gestion.repository.EmpresaRepository;
import co.edu.javeriana.bpmn.gestion.repository.UsuarioRepository;
import co.edu.javeriana.bpmn.gestion.service.ProcesoService;
import co.edu.javeriana.bpmn.gestion.service.RolProcesoService;
import co.edu.javeriana.bpmn.modelado.dto.ActividadRequest;
import co.edu.javeriana.bpmn.modelado.dto.ArcoRequest;
import co.edu.javeriana.bpmn.modelado.dto.CampoRequest;
import co.edu.javeriana.bpmn.modelado.dto.CorrelacionRequest;
import co.edu.javeriana.bpmn.modelado.dto.EnvioExternoRequest;
import co.edu.javeriana.bpmn.modelado.dto.EventoRequest;
import co.edu.javeriana.bpmn.modelado.dto.GatewayRequest;
import co.edu.javeriana.bpmn.modelado.dto.LaneRequest;
import co.edu.javeriana.bpmn.modelado.dto.MensajeCatchRequest;
import co.edu.javeriana.bpmn.modelado.dto.MensajeThrowRequest;
import co.edu.javeriana.bpmn.modelado.dto.PoolRequest;
import co.edu.javeriana.bpmn.modelado.entity.AccionSinCaso;
import co.edu.javeriana.bpmn.modelado.entity.PoliticaFallo;
import co.edu.javeriana.bpmn.modelado.entity.TipoActividad;
import co.edu.javeriana.bpmn.modelado.entity.TipoDato;
import co.edu.javeriana.bpmn.modelado.entity.TipoDestinoExterno;
import co.edu.javeriana.bpmn.modelado.entity.TipoGateway;
import co.edu.javeriana.bpmn.modelado.entity.TipoPool;
import co.edu.javeriana.bpmn.modelado.entity.VarianteEvento;
import co.edu.javeriana.bpmn.modelado.service.ActividadService;
import co.edu.javeriana.bpmn.modelado.service.ArcoService;
import co.edu.javeriana.bpmn.modelado.service.EventoService;
import co.edu.javeriana.bpmn.modelado.service.GatewayService;
import co.edu.javeriana.bpmn.modelado.service.LaneService;
import co.edu.javeriana.bpmn.modelado.service.MensajeService;
import co.edu.javeriana.bpmn.modelado.service.PoolService;
import co.edu.javeriana.bpmn.security.ApiPrincipal;

/**
 * Datos de demostración para desarrollo (perfil default) y para Docker (perfil demo).
 * Las empresas y usuarios se crean con los repositorios (único punto donde se saltan las capas);
 * los procesos se modelan con los mismos servicios de la API, así que cumplen todas sus reglas.
 * Es idempotente: si ya hay empresas, no hace nada.
 */
@Component
@Profile({"default", "demo"})
@Transactional // muchas consultas: commit al final o rollback completo
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);
    private static final String TALENTO_HUMANO = "Talento humano";
    /** Clave de correlación de los mensajes de compras (HU-28). */
    private static final String NUMERO_ORDEN = "numeroOrden";

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProcesoService procesoService;
    private final RolProcesoService rolProcesoService;
    private final PoolService poolService;
    private final LaneService laneService;
    private final ActividadService actividadService;
    private final GatewayService gatewayService;
    private final EventoService eventoService;
    private final ArcoService arcoService;
    private final MensajeService mensajeService;

    public DataLoader(EmpresaRepository empresaRepository, UsuarioRepository usuarioRepository,
                      PasswordEncoder passwordEncoder, ProcesoService procesoService,
                      RolProcesoService rolProcesoService, PoolService poolService, LaneService laneService,
                      ActividadService actividadService, GatewayService gatewayService, EventoService eventoService,
                      ArcoService arcoService, MensajeService mensajeService) {
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.procesoService = procesoService;
        this.rolProcesoService = rolProcesoService;
        this.poolService = poolService;
        this.laneService = laneService;
        this.actividadService = actividadService;
        this.gatewayService = gatewayService;
        this.eventoService = eventoService;
        this.arcoService = arcoService;
        this.mensajeService = mensajeService;
    }

    @Override
    public void run(String... args) {
        if (empresaRepository.count() > 0) {
            log.info("Datos de demostración ya cargados");
            return;
        }

        Empresa demo = empresa("Facimus Demo S.A.S.", "900123456-1", "admin@demo.com");
        Usuario adminDemo = usuario(demo, "Administradora Demo", "admin@demo.com", "admin123", RolAcceso.ADMINISTRADOR);
        usuario(demo, "Editor Demo", "editor@demo.com", "editor123", RolAcceso.EDITOR);
        usuario(demo, "Lector Demo", "lector@demo.com", "lector123", RolAcceso.LECTOR);

        Empresa andina = empresa("Comercial Andina Ltda.", "800987654-3", "admin@andina.com");
        Usuario adminAndina = usuario(andina, "Administrador Andina", "admin@andina.com", "admin123", RolAcceso.ADMINISTRADOR);
        usuario(andina, "Editora Andina", "editor@andina.com", "editor123", RolAcceso.EDITOR);
        usuario(andina, "Lector Andina", "lector@andina.com", "lector123", RolAcceso.LECTOR);

        try {
            como(adminDemo);
            Map<String, Long> roles = roles(demo.getId(), "Empleado", "Jefe inmediato", "Talento Humano",
                    "Analista", "Supervisor", "Auditor", "Vendedor");
            long vacaciones = vacaciones(demo.getId(), roles);
            procesoService.compartir(vacaciones, new CompartirRequest(true, List.of(andina.getNit())), demo.getId());
            compras(demo.getId(), roles);
            long obsoleto = procesoService.crear(new ProcesoRequest("Control de calidad 2024",
                    "Proceso reemplazado por la nueva versión", "Calidad"), demo.getId()).id();
            procesoService.eliminar(obsoleto, demo.getId());

            como(adminAndina);
            Map<String, Long> rolesAndina = roles(andina.getId(), "Coordinador");
            vacacionesAndina(andina.getId(), rolesAndina);
        } finally {
            SecurityContextHolder.clearContext();
        }
        log.info("Datos de demostración cargados: 2 empresas, 6 usuarios y 4 procesos");
    }

    /** El proceso de solicitud de vacaciones del enunciado, publicado y coherente. */
    private long vacaciones(Long empresa, Map<String, Long> roles) {
        long proceso = procesoService.crear(new ProcesoRequest("Solicitud de vacaciones",
                "Radicación, revisión y aprobación de las vacaciones de un empleado", TALENTO_HUMANO), empresa).id();
        long pool = poolService.listar(proceso, empresa).get(0).id();
        long correo = poolService.crear(proceso, new PoolRequest("Servicio de correo", TipoPool.SISTEMA_EXTERNO, true), empresa).id();
        long empleado = lane(pool, roles.get("Empleado"), empresa);
        long jefe = lane(pool, roles.get("Jefe inmediato"), empresa);
        long th = lane(pool, roles.get("Talento Humano"), empresa);

        long inicio = eventoService.crear(pool, new EventoRequest(EventoRequest.TipoEvento.INICIO, "Solicitud", empleado, 40, 0), empresa).id();
        long radicar = actividad(empleado, "Radicar solicitud", TipoActividad.USUARIO, 160, empresa);
        long revisar = actividad(jefe, "Revisar solicitud", TipoActividad.USUARIO, 320, empresa);
        long decision = gatewayService.crear(jefe, new GatewayRequest("¿Aprobada?", TipoGateway.EXCLUSIVO, null, 480, 0), empresa)
                .gateway().id();
        long rechazo = actividad(jefe, "Registrar rechazo", TipoActividad.MANUAL, 640, empresa);
        long actualizar = actividad(th, "Actualizar días", TipoActividad.SERVICIO, 640, empresa);
        var notificacion = mensajeService.crearEnvioExterno(proceso, new EnvioExternoRequest(pool, th,
                EnvioExternoRequest.ElementoEnvio.EVENTO, "NotificarAprobacion", null, correo, TipoDestinoExterno.CORREO,
                List.of(new CampoRequest("numeroSolicitud", TipoDato.TEXTO), new CampoRequest("empleado", TipoDato.TEXTO),
                        new CampoRequest("diasAprobados", TipoDato.NUMERO)),
                "Después de actualizar los días de vacaciones", PoliticaFallo.CONTINUAR, null, 800, 0), empresa).mensaje();
        mensajeService.definirCorrelacion(notificacion.id(),
                new CorrelacionRequest("numeroSolicitud", AccionSinCaso.DESCARTAR), empresa);
        long finAprobada = eventoService.crear(pool, new EventoRequest(EventoRequest.TipoEvento.FIN, "Vacaciones aprobadas", th, 960, 0), empresa).id();
        long finRechazada = eventoService.crear(pool, new EventoRequest(EventoRequest.TipoEvento.FIN, "Solicitud rechazada", jefe, 800, 0), empresa).id();

        arco(proceso, inicio, radicar, null, empresa);
        arco(proceso, radicar, revisar, null, empresa);
        arco(proceso, revisar, decision, null, empresa);
        arco(proceso, decision, actualizar, "Sí", empresa);
        arco(proceso, decision, rechazo, "No", empresa);
        arco(proceso, actualizar, notificacion.nodo().id(), null, empresa);
        arco(proceso, notificacion.nodo().id(), finAprobada, null, empresa);
        arco(proceso, rechazo, finRechazada, null, empresa);

        procesoService.actualizar(proceso, new ProcesoActualizacionRequest("Solicitud de vacaciones",
                "Radicación, revisión y aprobación de las vacaciones de un empleado", TALENTO_HUMANO,
                EstadoProceso.PUBLICADO, null), empresa);
        return proceso;
    }

    /** Colaboración con un proveedor: Message Throw y Message Catch correlacionados por número de orden. */
    private void compras(Long empresa, Map<String, Long> roles) {
        long proceso = procesoService.crear(new ProcesoRequest("Aprobación de compras",
                "Orden de compra enviada al proveedor y confirmación de despacho", "Compras"), empresa).id();
        long pool = poolService.listar(proceso, empresa).get(0).id();
        long proveedor = poolService.crear(proceso, new PoolRequest("Proveedor", TipoPool.PROVEEDOR, false), empresa).id();
        long analista = lane(pool, roles.get("Analista"), empresa);
        long supervisor = lane(pool, roles.get("Supervisor"), empresa);
        long vendedor = lane(proveedor, roles.get("Vendedor"), empresa);
        List<CampoRequest> orden = List.of(new CampoRequest(NUMERO_ORDEN, TipoDato.TEXTO), new CampoRequest("valor", TipoDato.DECIMAL));

        long inicio = eventoService.crear(pool, new EventoRequest(EventoRequest.TipoEvento.INICIO, "Necesidad de compra", analista, 40, 0), empresa).id();
        long registrar = actividad(analista, "Registrar orden", TipoActividad.USUARIO, 160, empresa);
        long aprobar = actividad(supervisor, "Aprobar orden", TipoActividad.USUARIO, 320, empresa);
        var envioOrden = mensajeService.crearThrow(proceso, new MensajeThrowRequest(pool, supervisor, "OrdenDeCompra",
                VarianteEvento.INTERMEDIO, proveedor, orden, NUMERO_ORDEN, 480, 0), empresa).mensaje();
        var esperaDespacho = mensajeService.crearCatch(proceso, new MensajeCatchRequest(pool, analista, "ConfirmacionDespacho",
                VarianteEvento.INTERMEDIO, false, List.of(new CampoRequest(NUMERO_ORDEN, TipoDato.TEXTO),
                new CampoRequest("fechaDespacho", TipoDato.FECHA)), List.of(), NUMERO_ORDEN, 640, 0), empresa).mensaje();
        long recibir = actividad(analista, "Recibir mercancía", TipoActividad.MANUAL, 800, empresa);
        long fin = eventoService.crear(pool, new EventoRequest(EventoRequest.TipoEvento.FIN, "Compra cerrada", analista, 960, 0), empresa).id();

        var llegaOrden = mensajeService.crearCatch(proceso, new MensajeCatchRequest(proveedor, vendedor, "OrdenDeCompra",
                VarianteEvento.INICIO, false, orden, List.of(), NUMERO_ORDEN, 480, 0), empresa).mensaje();
        long despachar = actividad(vendedor, "Despachar pedido", TipoActividad.MANUAL, 640, empresa);
        var confirmar = mensajeService.crearThrow(proceso, new MensajeThrowRequest(proveedor, vendedor, "ConfirmacionDespacho",
                VarianteEvento.FIN, pool, List.of(new CampoRequest(NUMERO_ORDEN, TipoDato.TEXTO),
                new CampoRequest("fechaDespacho", TipoDato.FECHA)), NUMERO_ORDEN, 800, 0), empresa).mensaje();

        arco(proceso, inicio, registrar, null, empresa);
        arco(proceso, registrar, aprobar, null, empresa);
        arco(proceso, aprobar, envioOrden.nodo().id(), null, empresa);
        arco(proceso, envioOrden.nodo().id(), esperaDespacho.nodo().id(), null, empresa);
        arco(proceso, esperaDespacho.nodo().id(), recibir, null, empresa);
        arco(proceso, recibir, fin, null, empresa);
        arco(proceso, llegaOrden.nodo().id(), despachar, null, empresa);
        arco(proceso, despachar, confirmar.nodo().id(), null, empresa);
    }

    /** Mismo nombre de proceso en otra empresa: los espacios son independientes (HU-01). */
    private void vacacionesAndina(Long empresa, Map<String, Long> roles) {
        long proceso = procesoService.crear(new ProcesoRequest("Solicitud de vacaciones",
                "Versión simplificada de Comercial Andina", TALENTO_HUMANO), empresa).id();
        long pool = poolService.listar(proceso, empresa).get(0).id();
        long coordinador = lane(pool, roles.get("Coordinador"), empresa);
        long inicio = eventoService.crear(pool, new EventoRequest(EventoRequest.TipoEvento.INICIO, null, coordinador, 40, 0), empresa).id();
        long aprobar = actividad(coordinador, "Aprobar vacaciones", TipoActividad.USUARIO, 200, empresa);
        long fin = eventoService.crear(pool, new EventoRequest(EventoRequest.TipoEvento.FIN, null, coordinador, 360, 0), empresa).id();
        arco(proceso, inicio, aprobar, null, empresa);
        arco(proceso, aprobar, fin, null, empresa);
    }

    private Map<String, Long> roles(Long empresa, String... nombres) {
        Map<String, Long> roles = new java.util.HashMap<>();
        for (String nombre : nombres) {
            roles.put(nombre, rolProcesoService.crear(new RolProcesoRequest(nombre, "Rol de proceso " + nombre.toLowerCase()), empresa).id());
        }
        return roles;
    }

    private long lane(long pool, long rol, Long empresa) {
        return laneService.crear(pool, new LaneRequest(rol), empresa).id();
    }

    private long actividad(long lane, String nombre, TipoActividad tipo, int posX, Long empresa) {
        return actividadService.crear(lane, new ActividadRequest(nombre, tipo, null, posX, 0), empresa).id();
    }

    private void arco(long proceso, long origen, long destino, String condicion, Long empresa) {
        arcoService.crear(new ArcoRequest(proceso, origen, destino, condicion, condicion), empresa);
    }

    private Empresa empresa(String nombre, String nit, String correo) {
        return empresaRepository.save(Empresa.builder().nombre(nombre).nit(nit).correoContacto(correo).build());
    }

    private Usuario usuario(Empresa empresa, String nombre, String correo, String clave, RolAcceso rol) {
        Usuario usuario = Usuario.builder()
                .nombre(nombre).correo(correo).password(passwordEncoder.encode(clave)).rol(rol).build();
        usuario.setEmpresaId(empresa.getId());
        return usuarioRepository.save(usuario);
    }

    /**
     * Los servicios exigen un usuario autenticado (permisos e historial): se usa el administrador de cada empresa.
     * Antes de cambiar de usuario se escriben los cambios pendientes: la auditoría (modificadoPor) se calcula al
     * escribir en la base y debe quedar con el usuario que hizo el cambio, no con el siguiente.
     */
    private void como(Usuario usuario) {
        usuarioRepository.flush();
        ApiPrincipal principal = ApiPrincipal.de(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}

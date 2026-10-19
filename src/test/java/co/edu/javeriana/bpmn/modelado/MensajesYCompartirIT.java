package co.edu.javeriana.bpmn.modelado;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.javeriana.bpmn.soporte.IntegracionBase;

/** HU-23 y HU-25 a HU-28 de punta a punta: colaboración entre pools, correlación y compartición. */
class MensajesYCompartirIT extends IntegracionBase {

    private String admin;
    private String editor;
    private String lector;
    private String adminB;
    private long proceso;
    private long poolEmpresa;
    private long proveedor;
    private long correo;
    private long laneAnalista;
    private long laneVendedor;

    @BeforeEach
    void datos() throws Exception {
        admin = api.registrarEmpresaYEntrar("Empresa A", "900111222-1", "admin@a.com");
        editor = api.crearUsuarioYEntrar(admin, "editor@a.com", "EDITOR");
        lector = api.crearUsuarioYEntrar(admin, "lector@a.com", "LECTOR");
        adminB = api.registrarEmpresaYEntrar("Empresa B", "800333444-5", "admin@b.com");
        proceso = api.id(api.post(editor, "/api/v1/procesos", Map.of("nombre", "Compras", "categoria", "Compras")), "id");
        poolEmpresa = api.id(api.get(editor, "/api/v1/procesos/{id}/pools", proceso), "0.id");
        proveedor = api.id(api.post(editor, "/api/v1/procesos/{id}/pools", Map.of("nombre", "Proveedor", "tipo", "PROVEEDOR"), proceso), "id");
        correo = api.id(api.post(editor, "/api/v1/procesos/{id}/pools", Map.of("nombre", "Servicio de correo", "tipo", "SISTEMA_EXTERNO"), proceso), "id");
        laneAnalista = lane(poolEmpresa, "Analista");
        laneVendedor = lane(proveedor, "Vendedor");
    }

    private long lane(long pool, String rol) throws Exception {
        long idRol = api.id(api.post(admin, "/api/v1/roles-proceso", Map.of("nombre", rol)), "id");
        return api.id(api.post(editor, "/api/v1/pools/{id}/lanes", Map.of("rolProcesoId", idRol), pool), "id");
    }

    private Map<String, Object> throwA(String nombre, long destino) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("poolId", poolEmpresa);
        cuerpo.put("laneId", laneAnalista);
        cuerpo.put("nombre", nombre);
        cuerpo.put("variante", "INTERMEDIO");
        cuerpo.put("poolDestinoId", destino);
        cuerpo.put("campos", List.of(Map.of("nombre", "numeroOrden", "tipoDato", "TEXTO"),
                Map.of("nombre", "valor", "tipoDato", "DECIMAL")));
        return cuerpo;
    }

    private Map<String, Object> catchProveedor(String nombre, String variante) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("poolId", proveedor);
        cuerpo.put("laneId", laneVendedor);
        cuerpo.put("nombre", nombre);
        cuerpo.put("variante", variante);
        return cuerpo;
    }

    private long id(org.springframework.test.web.servlet.ResultActions r, String campo) throws Exception {
        return api.id(r, campo);
    }

    // ---------- HU-25 Message Throw ----------

    @Test
    void HU25_throwHaciaOtroPool_ConContenidoYAvisoSiNoHayReceptor() throws Exception {
        api.post(editor, "/api/v1/procesos/{id}/mensajes/throw", throwA("OrdenDeCompra", proveedor), proceso)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje.direccion").value("ENVIO"))
                .andExpect(jsonPath("$.mensaje.nodo.tipo").value("MESSAGE_THROW"))
                .andExpect(jsonPath("$.mensaje.poolDestinoId").value(proveedor))
                .andExpect(jsonPath("$.mensaje.campos[*].nombre", contains("numeroOrden", "valor")))
                .andExpect(jsonPath("$.mensaje.campos[1].tipoDato").value("DECIMAL"))
                .andExpect(jsonPath("$.mensaje.nodoDestinoId").doesNotExist())
                .andExpect(jsonPath("$.advertencias[*].codigo", contains("MENSAJE_SIN_RECEPTOR")))
                .andExpect(jsonPath("$.advertencias[0].severidad").value("AVISO"));
    }

    @Test
    void HU25_conCatchHomonimoEnElDestino_ElFlujoLlegaAlCatchYNoHayAviso() throws Exception {
        long envio = id(api.post(editor, "/api/v1/procesos/{id}/mensajes/throw", throwA("OrdenDeCompra", proveedor), proceso), "mensaje.id");
        long recepcion = id(api.post(editor, "/api/v1/procesos/{id}/mensajes/catch", catchProveedor("ordendecompra", "INICIO"), proceso), "mensaje.nodo.id");

        api.get(editor, "/api/v1/mensajes/{id}", envio)
                .andExpect(jsonPath("$.mensaje.nodoDestinoId").value(recepcion))
                .andExpect(jsonPath("$.advertencias", empty()));
        api.get(lector, "/api/v1/procesos/{id}/diagrama", proceso)
                .andExpect(jsonPath("$.mensajes", hasSize(2)))
                .andExpect(jsonPath("$.mensajes[?(@.direccion == 'ENVIO')].nodoDestinoId", contains((int) recepcion)));
    }

    @Test
    void HU25_reglasDelFlujoDeMensaje() throws Exception {
        long otroProceso = api.id(api.post(editor, "/api/v1/procesos", Map.of("nombre", "Otro", "categoria", "X")), "id");
        long poolOtro = api.id(api.get(editor, "/api/v1/procesos/{id}/pools", otroProceso), "0.id");
        long procesoB = api.id(api.post(adminB, "/api/v1/procesos", Map.of("nombre", "Ajeno", "categoria", "X")), "id");
        long poolB = api.id(api.get(adminB, "/api/v1/procesos/{id}/pools", procesoB), "0.id");

        api.post(editor, "/api/v1/procesos/{id}/mensajes/throw", throwA("X", poolEmpresa), proceso)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail", containsString("cruza de un pool a otro")));
        api.post(editor, "/api/v1/procesos/{id}/mensajes/throw", throwA("X", poolOtro), proceso)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail", containsString("mismo diagrama")));
        api.post(editor, "/api/v1/procesos/{id}/mensajes/throw", throwA("X", poolB), proceso)
                .andExpect(status().isNotFound());
        Map<String, Object> inicio = throwA("X", proveedor);
        inicio.put("variante", "INICIO");
        api.post(editor, "/api/v1/procesos/{id}/mensajes/throw", inicio, proceso).andExpect(status().isConflict());
        Map<String, Object> desdeCajaNegra = throwA("X", proveedor);
        desdeCajaNegra.put("poolId", correo);
        desdeCajaNegra.remove("laneId");
        api.post(editor, "/api/v1/procesos/{id}/mensajes/throw", desdeCajaNegra, proceso).andExpect(status().isConflict());
        Map<String, Object> repetido = throwA("X", proveedor);
        repetido.put("campos", List.of(Map.of("nombre", "a", "tipoDato", "TEXTO"), Map.of("nombre", "A", "tipoDato", "NUMERO")));
        api.post(editor, "/api/v1/procesos/{id}/mensajes/throw", repetido, proceso).andExpect(status().isConflict());
        Map<String, Object> tipoMalo = throwA("X", proveedor);
        tipoMalo.put("campos", List.of(Map.of("nombre", "a", "tipoDato", "IMAGEN")));
        api.post(editor, "/api/v1/procesos/{id}/mensajes/throw", tipoMalo, proceso).andExpect(status().isBadRequest());
        api.post(lector, "/api/v1/procesos/{id}/mensajes/throw", throwA("X", proveedor), proceso).andExpect(status().isForbidden());
        api.get(lector, "/api/v1/procesos/{id}/mensajes", proceso).andExpect(jsonPath("$", empty()));
    }

    @Test
    void HU25_unArcoNoReemplazaAlMensaje_NoCruzaPools() throws Exception {
        long envio = id(api.post(editor, "/api/v1/procesos/{id}/mensajes/throw", throwA("OrdenDeCompra", proveedor), proceso), "mensaje.nodo.id");
        long recepcion = id(api.post(editor, "/api/v1/procesos/{id}/mensajes/catch", catchProveedor("OrdenDeCompra", "INICIO"), proceso), "mensaje.nodo.id");

        api.post(editor, "/api/v1/arcos", Map.of("procesoId", proceso, "origenId", envio, "destinoId", recepcion))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail", containsString("se modela como mensaje")));
    }

    // ---------- HU-26 Notificación externa ----------

    private Map<String, Object> externo(String elemento, String politica) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("poolId", poolEmpresa);
        cuerpo.put("laneId", laneAnalista);
        cuerpo.put("elemento", elemento);
        cuerpo.put("nombre", "NotificarAprobacion");
        cuerpo.put("poolDestinoId", correo);
        cuerpo.put("tipoDestino", "CORREO");
        cuerpo.put("momento", "Después de aprobar la orden");
        cuerpo.put("politicaFallo", politica);
        cuerpo.put("campos", List.of(Map.of("nombre", "correoSolicitante", "tipoDato", "TEXTO")));
        return cuerpo;
    }

    @Test
    void HU26_notificacionAlSistemaExternoDeCajaNegra_DocumentaDestinoDatosMomentoYFallo() throws Exception {
        api.post(editor, "/api/v1/procesos/{id}/mensajes/externos", externo("EVENTO", "CONTINUAR"), proceso)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje.tipoDestino").value("CORREO"))
                .andExpect(jsonPath("$.mensaje.momento").value("Después de aprobar la orden"))
                .andExpect(jsonPath("$.mensaje.politicaFallo").value("CONTINUAR"))
                .andExpect(jsonPath("$.mensaje.nodo.tipo").value("MESSAGE_THROW"))
                .andExpect(jsonPath("$.advertencias[*].codigo", not(hasItem("MENSAJE_SIN_RECEPTOR"))));

        Map<String, Object> tarea = externo("TAREA", "FINALIZAR_PROCESO");
        tarea.put("nombreTarea", "Enviar correo de aprobación");
        tarea.put("tipoDestino", "SERVICIO_WEB");
        api.post(editor, "/api/v1/procesos/{id}/mensajes/externos", tarea, proceso)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje.nodo.tipo").value("ACTIVIDAD"))
                .andExpect(jsonPath("$.mensaje.nodo.tipoActividad").value("ENVIO"))
                .andExpect(jsonPath("$.mensaje.nodo.nombre").value("Enviar correo de aprobación"));
    }

    @Test
    void HU26_reglasDeLaNotificacionExterna() throws Exception {
        Map<String, Object> aParticipante = externo("EVENTO", "CONTINUAR");
        aParticipante.put("poolDestinoId", proveedor);
        api.post(editor, "/api/v1/procesos/{id}/mensajes/externos", aParticipante, proceso)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail", containsString("caja negra")));
        api.post(editor, "/api/v1/procesos/{id}/mensajes/externos", externo("EVENTO", "DERIVAR_A_ACTIVIDAD"), proceso)
                .andExpect(status().isBadRequest());
        Map<String, Object> sinTipo = externo("EVENTO", "CONTINUAR");
        sinTipo.remove("tipoDestino");
        api.post(editor, "/api/v1/procesos/{id}/mensajes/externos", sinTipo, proceso).andExpect(status().isBadRequest());
        Map<String, Object> tareaSinNombre = externo("TAREA", "CONTINUAR");
        api.post(editor, "/api/v1/procesos/{id}/mensajes/externos", tareaSinNombre, proceso).andExpect(status().isBadRequest());

        long actividadProveedor = id(api.post(editor, "/api/v1/lanes/{id}/actividades",
                Map.of("nombre", "Despachar", "tipoActividad", "TAREA"), laneVendedor), "id");
        Map<String, Object> derivaAOtroPool = externo("EVENTO", "DERIVAR_A_ACTIVIDAD");
        derivaAOtroPool.put("actividadManejoErrorId", actividadProveedor);
        api.post(editor, "/api/v1/procesos/{id}/mensajes/externos", derivaAOtroPool, proceso)
                .andExpect(status().isConflict());
    }

    @Test
    void HU26_derivarAActividad_LaActividadDeManejoNoSeEliminaYElDestinoSigueSiendoCajaNegra() throws Exception {
        long manejo = id(api.post(editor, "/api/v1/lanes/{id}/actividades",
                Map.of("nombre", "Avisar por teléfono", "tipoActividad", "MANUAL"), laneAnalista), "id");
        Map<String, Object> deriva = externo("EVENTO", "DERIVAR_A_ACTIVIDAD");
        deriva.put("actividadManejoErrorId", manejo);
        api.post(editor, "/api/v1/procesos/{id}/mensajes/externos", deriva, proceso)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje.actividadManejoErrorId").value(manejo));

        api.delete(admin, "/api/v1/actividades/{id}", manejo)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail", containsString("manejo de error")));
        api.put(editor, "/api/v1/pools/{id}", Map.of("nombre", "Servicio de correo", "tipo", "SISTEMA_EXTERNO", "cajaNegra", false), correo)
                .andExpect(status().isConflict());
        api.delete(editor, "/api/v1/pools/{id}", correo)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail", containsString("destino de 1 mensaje")));
    }

    // ---------- HU-27 Message Catch ----------

    @Test
    void HU27_catchSinEmisorEsError_HastaMarcarloComoOrigenExterno() throws Exception {
        long catchId = id(api.post(editor, "/api/v1/procesos/{id}/mensajes/catch", catchProveedor("PagoRecibido", "INICIO"), proceso)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje.direccion").value("RECEPCION"))
                .andExpect(jsonPath("$.mensaje.nodo.variante").value("INICIO"))
                .andExpect(jsonPath("$.advertencias[*].codigo", contains("CATCH_SIN_EMISOR")))
                .andExpect(jsonPath("$.advertencias[0].severidad").value("ERROR")), "mensaje.id");

        api.put(editor, "/api/v1/mensajes/{id}", Map.of("nombre", "PagoRecibido", "origenExterno", true), catchId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje.origenExterno").value(true))
                .andExpect(jsonPath("$.advertencias", empty()));
    }

    @Test
    void HU27_variantesYDatosEsperados() throws Exception {
        long revisar = id(api.post(editor, "/api/v1/lanes/{id}/actividades",
                Map.of("nombre", "Revisar pago", "tipoActividad", "USUARIO"), laneVendedor), "id");
        long otroProceso = api.id(api.post(editor, "/api/v1/procesos", Map.of("nombre", "Otro", "categoria", "X")), "id");
        api.armarDiagramaMinimo(admin, otroProceso);
        long actividadAjena = api.id(api.get(editor, "/api/v1/procesos/{id}/diagrama", otroProceso), "nodos.1.id");

        Map<String, Object> intermedio = catchProveedor("PagoRecibido", "INTERMEDIO");
        intermedio.put("origenExterno", true);
        intermedio.put("campos", List.of(Map.of("nombre", "valorPagado", "tipoDato", "DECIMAL")));
        intermedio.put("actividadesQueUsanIds", List.of(revisar));
        api.post(editor, "/api/v1/procesos/{id}/mensajes/catch", intermedio, proceso)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje.actividadesQueUsanIds", contains((int) revisar)))
                .andExpect(jsonPath("$.mensaje.campos[0].nombre").value("valorPagado"))
                .andExpect(jsonPath("$.advertencias[*].codigo", contains("CORRELACION_FALTANTE")));

        api.post(editor, "/api/v1/procesos/{id}/mensajes/catch", catchProveedor("X", "FIN"), proceso).andExpect(status().isConflict());
        Map<String, Object> ajena = catchProveedor("Y", "INTERMEDIO");
        ajena.put("actividadesQueUsanIds", List.of(actividadAjena));
        api.post(editor, "/api/v1/procesos/{id}/mensajes/catch", ajena, proceso).andExpect(status().isConflict());
    }

    @Test
    void HU27_catchDeInicioNoAdmiteArcosEntrantes() throws Exception {
        long catchNodo = id(api.post(editor, "/api/v1/procesos/{id}/mensajes/catch", catchProveedor("Pedido", "INICIO"), proceso), "mensaje.nodo.id");
        long despachar = id(api.post(editor, "/api/v1/lanes/{id}/actividades",
                Map.of("nombre", "Despachar", "tipoActividad", "TAREA"), laneVendedor), "id");

        api.post(editor, "/api/v1/arcos", Map.of("procesoId", proceso, "origenId", despachar, "destinoId", catchNodo))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Un evento de inicio no puede tener arcos entrantes"));
        api.post(editor, "/api/v1/arcos", Map.of("procesoId", proceso, "origenId", catchNodo, "destinoId", despachar))
                .andExpect(status().isCreated());
    }

    // ---------- HU-28 Correlación ----------

    @Test
    void HU28_laClaveSeAplicaDeFormaCoherenteAlThrowYAlCatch() throws Exception {
        long envio = id(api.post(editor, "/api/v1/procesos/{id}/mensajes/throw", throwA("OrdenDeCompra", proveedor), proceso), "mensaje.id");
        long recepcion = id(api.post(editor, "/api/v1/procesos/{id}/mensajes/catch", catchProveedor("OrdenDeCompra", "INTERMEDIO"), proceso), "mensaje.id");

        api.put(editor, "/api/v1/mensajes/{id}/correlacion", Map.of("claveCorrelacion", "numeroOrden", "accionSinCaso", "DESCARTAR"), envio)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje.claveCorrelacion").value("numeroOrden"))
                .andExpect(jsonPath("$.mensaje.accionSinCaso").value("DESCARTAR"));
        api.get(editor, "/api/v1/mensajes/{id}", recepcion)
                .andExpect(jsonPath("$.mensaje.claveCorrelacion").value("numeroOrden"))
                .andExpect(jsonPath("$.advertencias", empty()));
        api.get(lector, "/api/v1/procesos/{id}/historial", proceso)
                .andExpect(jsonPath("$.content[0].elemento").value("CORRELACION"))
                .andExpect(jsonPath("$.content[0].detalle", containsString("aplicada también a 1 mensaje")));
        api.put(editor, "/api/v1/mensajes/{id}/correlacion", Map.of("claveCorrelacion", " "), envio).andExpect(status().isBadRequest());
    }

    @Test
    void HU28_claveDistintaEnThrowYCatch_EsIncoherente() throws Exception {
        Map<String, Object> envio = throwA("OrdenDeCompra", proveedor);
        envio.put("claveCorrelacion", "numeroOrden");
        api.post(editor, "/api/v1/procesos/{id}/mensajes/throw", envio, proceso);
        Map<String, Object> recepcion = catchProveedor("OrdenDeCompra", "INTERMEDIO");
        recepcion.put("claveCorrelacion", "nitCliente");
        api.post(editor, "/api/v1/procesos/{id}/mensajes/catch", recepcion, proceso);

        api.get(editor, "/api/v1/procesos/{id}/validacion", proceso)
                .andExpect(jsonPath("$[*].codigo", hasItem("CORRELACION_INCOHERENTE")));
    }

    @Test
    void HU28_dosMensajesDistintosConMismoNombreYClave_SonAmbiguos() throws Exception {
        long laneAuditor = lane(poolEmpresa, "Auditor");
        Map<String, Object> primero = throwA("PagoRecibido", proveedor);
        primero.put("claveCorrelacion", "numeroFactura");
        Map<String, Object> segundo = throwA("PagoRecibido", proveedor);
        segundo.put("claveCorrelacion", "NUMEROFACTURA");
        segundo.put("laneId", laneAuditor);
        api.post(editor, "/api/v1/procesos/{id}/mensajes/throw", primero, proceso);
        api.post(editor, "/api/v1/procesos/{id}/mensajes/throw", segundo, proceso)
                .andExpect(jsonPath("$.advertencias[*].codigo", hasItem("CORRELACION_AMBIGUA")));
    }

    // ---------- Colaboración completa ----------

    @Test
    void Colaboracion_procesoConDosPoolsYMensajes_SePublica() throws Exception {
        long inicioA = id(api.post(editor, "/api/v1/pools/{id}/eventos", Map.of("tipo", "INICIO", "laneId", laneAnalista), poolEmpresa), "id");
        long solicitar = id(api.post(editor, "/api/v1/lanes/{id}/actividades", Map.of("nombre", "Solicitar cotización", "tipoActividad", "USUARIO"), laneAnalista), "id");
        long envio = id(api.post(editor, "/api/v1/procesos/{id}/mensajes/throw", throwA("SolicitudCotizacion", proveedor), proceso), "mensaje.nodo.id");
        long finA = id(api.post(editor, "/api/v1/pools/{id}/eventos", Map.of("tipo", "FIN", "laneId", laneAnalista), poolEmpresa), "id");
        long recepcion = id(api.post(editor, "/api/v1/procesos/{id}/mensajes/catch", catchProveedor("SolicitudCotizacion", "INICIO"), proceso), "mensaje.nodo.id");
        long cotizar = id(api.post(editor, "/api/v1/lanes/{id}/actividades", Map.of("nombre", "Preparar cotización", "tipoActividad", "USUARIO"), laneVendedor), "id");
        long finP = id(api.post(editor, "/api/v1/pools/{id}/eventos", Map.of("tipo", "FIN", "laneId", laneVendedor), proveedor), "id");
        for (long[] par : new long[][] {{inicioA, solicitar}, {solicitar, envio}, {envio, finA}, {recepcion, cotizar}, {cotizar, finP}}) {
            api.post(editor, "/api/v1/arcos", Map.of("procesoId", proceso, "origenId", par[0], "destinoId", par[1]))
                    .andExpect(status().isCreated());
        }

        api.get(editor, "/api/v1/procesos/{id}/validacion", proceso).andExpect(jsonPath("$", empty()));
        api.put(editor, "/api/v1/procesos/{id}", Map.of("nombre", "Compras", "categoria", "Compras", "estado", "PUBLICADO"), proceso)
                .andExpect(status().isOk());

        // Publicado: borrar el catch dejaría el throw sin receptor y al proveedor sin inicio
        long mensajeCatch = api.id(api.get(editor, "/api/v1/procesos/{id}/mensajes", proceso), "1.id");
        api.delete(admin, "/api/v1/mensajes/{id}", mensajeCatch).andExpect(status().isConflict());
    }

    @Test
    void Mensajes_eliminarYAislamiento() throws Exception {
        long envio = id(api.post(editor, "/api/v1/procesos/{id}/mensajes/throw", throwA("OrdenDeCompra", proveedor), proceso), "mensaje.id");

        api.get(adminB, "/api/v1/mensajes/{id}", envio).andExpect(status().isNotFound());
        api.put(adminB, "/api/v1/mensajes/{id}", Map.of("nombre", "X"), envio).andExpect(status().isNotFound());
        api.delete(adminB, "/api/v1/mensajes/{id}", envio).andExpect(status().isNotFound());
        api.get(adminB, "/api/v1/procesos/{id}/mensajes", proceso).andExpect(status().isNotFound());
        api.put(editor, "/api/v1/mensajes/{id}", Map.of("nombre", "OrdenDeCompra", "origenExterno", true), envio)
                .andExpect(status().isConflict());

        api.get(editor, "/api/v1/mensajes/{id}/impacto", envio).andExpect(status().isOk());
        api.delete(editor, "/api/v1/mensajes/{id}", envio).andExpect(status().isForbidden());
        api.delete(admin, "/api/v1/mensajes/{id}", envio).andExpect(status().isNoContent());
        api.get(lector, "/api/v1/procesos/{id}/diagrama", proceso)
                .andExpect(jsonPath("$.mensajes", empty()))
                .andExpect(jsonPath("$.nodos", empty()));
    }

    // ---------- HU-23 Compartir ----------

    @Test
    void HU23_compartidoConOtraEmpresa_SoloLectura() throws Exception {
        api.put(admin, "/api/v1/procesos/{id}/compartir", Map.of("compartido", true, "nitsEmpresas", List.of("800333444-5")), proceso)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compartido").value(true))
                .andExpect(jsonPath("$.empresas[0].nombre").value("Empresa B"));

        api.get(adminB, "/api/v1/procesos/compartidos")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].empresaPropietaria").value("Empresa A"))
                .andExpect(jsonPath("$.content[0].proceso.propio").value(false));
        api.get(adminB, "/api/v1/procesos/{id}", proceso).andExpect(status().isOk()).andExpect(jsonPath("$.propio").value(false));
        api.get(adminB, "/api/v1/procesos/{id}/diagrama", proceso).andExpect(status().isOk())
                .andExpect(jsonPath("$.pools", hasSize(3)));
        api.get(adminB, "/api/v1/procesos/{id}/validacion", proceso).andExpect(status().isOk());

        // El modelo se comparte; los usuarios, el historial y el catálogo de roles no
        api.get(adminB, "/api/v1/procesos/{id}/historial", proceso).andExpect(status().isNotFound());
        api.get(adminB, "/api/v1/procesos/{id}/compartir", proceso).andExpect(status().isNotFound());
        api.get(adminB, "/api/v1/pools/{id}/roles-disponibles", poolEmpresa).andExpect(status().isNotFound());
        // Solo lectura: ninguna escritura
        api.put(adminB, "/api/v1/procesos/{id}", Map.of("nombre", "Robado", "categoria", "X", "estado", "BORRADOR"), proceso)
                .andExpect(status().isNotFound());
        api.delete(adminB, "/api/v1/procesos/{id}", proceso).andExpect(status().isNotFound());
        api.post(adminB, "/api/v1/procesos/{id}/pools", Map.of("nombre", "Intruso", "tipo", "CLIENTE"), proceso)
                .andExpect(status().isNotFound());
        api.post(adminB, "/api/v1/lanes/{id}/actividades", Map.of("nombre", "Intrusa", "tipoActividad", "TAREA"), laneAnalista)
                .andExpect(status().isNotFound());
        api.put(adminB, "/api/v1/procesos/{id}/compartir", Map.of("compartido", false), proceso).andExpect(status().isNotFound());
        api.get(adminB, "/api/v1/procesos").andExpect(jsonPath("$.totalElements").value(0));

        api.get(admin, "/api/v1/procesos/{id}/historial", proceso)
                .andExpect(jsonPath("$.content[0].tipo").value("COMPARTICION"))
                .andExpect(jsonPath("$.content[0].detalle", containsString("Empresa B (800333444-5)")));
    }

    @Test
    void HU23_porDefectoNoSeComparte_YDejarDeCompartirRetiraElAcceso() throws Exception {
        api.get(adminB, "/api/v1/procesos/{id}", proceso).andExpect(status().isNotFound());
        api.get(admin, "/api/v1/procesos/{id}/compartir", proceso).andExpect(jsonPath("$.compartido").value(false));

        api.put(admin, "/api/v1/procesos/{id}/compartir", Map.of("compartido", true, "nitsEmpresas", List.of("800333444-5")), proceso);
        api.get(adminB, "/api/v1/procesos/{id}", proceso).andExpect(status().isOk());
        api.put(admin, "/api/v1/procesos/{id}/compartir", Map.of("compartido", false), proceso)
                .andExpect(jsonPath("$.compartido").value(false));

        api.get(adminB, "/api/v1/procesos/{id}", proceso).andExpect(status().isNotFound());
        api.get(adminB, "/api/v1/procesos/compartidos").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void HU23_reglasDeComparticion() throws Exception {
        String adminC = api.registrarEmpresaYEntrar("Empresa C", "811555666-7", "admin@c.com");

        api.put(editor, "/api/v1/procesos/{id}/compartir", Map.of("compartido", true, "nitsEmpresas", List.of("800333444-5")), proceso)
                .andExpect(status().isForbidden());
        api.put(admin, "/api/v1/procesos/{id}/compartir", Map.of("compartido", true, "nitsEmpresas", List.of("123456789")), proceso)
                .andExpect(status().isNotFound());
        api.put(admin, "/api/v1/procesos/{id}/compartir", Map.of("compartido", true, "nitsEmpresas", List.of("900111222-1")), proceso)
                .andExpect(status().isConflict());
        api.put(admin, "/api/v1/procesos/{id}/compartir", Map.of("compartido", true), proceso).andExpect(status().isConflict());
        api.put(admin, "/api/v1/procesos/{id}/compartir", Map.of("compartido", true, "nitsEmpresas", List.of("no-es-nit")), proceso)
                .andExpect(status().isBadRequest());

        api.put(admin, "/api/v1/procesos/{id}/compartir", Map.of("compartido", true, "nitsEmpresas", List.of("800333444-5")), proceso);
        api.get(adminC, "/api/v1/procesos/{id}", proceso).andExpect(status().isNotFound());
        api.delete(admin, "/api/v1/procesos/{id}", proceso).andExpect(status().isNoContent());
        api.get(adminB, "/api/v1/procesos/{id}", proceso).andExpect(status().isNotFound());
    }
}

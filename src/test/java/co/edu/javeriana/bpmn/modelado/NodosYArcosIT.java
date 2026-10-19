package co.edu.javeriana.bpmn.modelado;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import co.edu.javeriana.bpmn.soporte.IntegracionBase;

/**
 * HU-08 a HU-16: se arma el proceso de vacaciones del enunciado y se intenta romper
 * cada regla del modelo BPMN.
 */
class NodosYArcosIT extends IntegracionBase {

    private String admin;
    private String editor;
    private String lector;
    private String otraEmpresa;
    private long proceso;
    private long pool;
    private long laneEmpleado;
    private long laneJefe;
    private long laneTH;

    // Elementos del proceso de vacaciones
    private long inicio;
    private long radicar;
    private long revisar;
    private long decision;
    private long rechazo;
    private long actualizar;
    private long fin;
    private long arcoSi;
    private long arcoNo;

    @BeforeEach
    void datos() throws Exception {
        admin = api.registrarEmpresaYEntrar("Empresa A", "900111222-1", "admin@a.com");
        editor = api.crearUsuarioYEntrar(admin, "editor@a.com", "EDITOR");
        lector = api.crearUsuarioYEntrar(admin, "lector@a.com", "LECTOR");
        otraEmpresa = api.registrarEmpresaYEntrar("Empresa B", "800333444-5", "admin@b.com");
        proceso = api.id(api.post(editor, "/api/v1/procesos", Map.of("nombre", "Vacaciones", "categoria", "TH")), "id");
        pool = api.id(api.get(editor, "/api/v1/procesos/{id}/pools", proceso), "0.id");
        laneEmpleado = lane("Empleado");
        laneJefe = lane("Jefe inmediato");
        laneTH = lane("Talento Humano");
    }

    private long lane(String rol) throws Exception {
        long idRol = api.id(api.post(admin, "/api/v1/roles-proceso", Map.of("nombre", rol)), "id");
        return api.id(api.post(editor, "/api/v1/pools/{id}/lanes", Map.of("rolProcesoId", idRol), pool), "id");
    }

    private long actividad(long lane, String nombre) throws Exception {
        return api.id(api.post(editor, "/api/v1/lanes/{id}/actividades",
                Map.of("nombre", nombre, "tipoActividad", "USUARIO", "posX", 100, "posY", 40), lane)
                .andExpect(status().isCreated()), "id");
    }

    private long evento(String tipo, long lane) throws Exception {
        return api.id(api.post(editor, "/api/v1/pools/{id}/eventos", Map.of("tipo", tipo, "laneId", lane), pool)
                .andExpect(status().isCreated()), "id");
    }

    private long arco(long origen, long destino, String condicion) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>(Map.of("procesoId", proceso, "origenId", origen, "destinoId", destino));
        if (condicion != null) {
            cuerpo.put("condicion", condicion);
            cuerpo.put("etiqueta", condicion);
        }
        return api.id(api.post(editor, "/api/v1/arcos", cuerpo).andExpect(status().isCreated()), "id");
    }

    /** El proceso de solicitud de vacaciones del enunciado. */
    private void armarVacaciones() throws Exception {
        inicio = evento("INICIO", laneEmpleado);
        radicar = actividad(laneEmpleado, "Radicar solicitud");
        revisar = actividad(laneJefe, "Revisar solicitud");
        decision = api.id(api.post(editor, "/api/v1/lanes/{id}/gateways",
                Map.of("nombre", "¿Aprobada?", "tipoGateway", "EXCLUSIVO"), laneJefe).andExpect(status().isCreated()),
                "gateway.id");
        rechazo = actividad(laneJefe, "Registrar rechazo");
        actualizar = actividad(laneTH, "Actualizar días");
        fin = evento("FIN", laneTH);
        arco(inicio, radicar, null);
        arco(radicar, revisar, null);
        arco(revisar, decision, null);
        arcoSi = arco(decision, actualizar, "Sí");
        arcoNo = arco(decision, rechazo, "No");
        arco(actualizar, fin, null);
        arco(rechazo, fin, null);
    }

    private Map<String, Object> publicar() {
        return Map.of("nombre", "Vacaciones", "categoria", "TH", "estado", "PUBLICADO");
    }

    // ---------- Diagrama completo ----------

    @Test
    void HU07_procesoDeVacaciones_DiagramaCompletoCoherenteYPublicable() throws Exception {
        armarVacaciones();

        api.get(lector, "/api/v1/procesos/{id}/diagrama", proceso)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pools[0].lanes[*].nombre", contains("Empleado", "Jefe inmediato", "Talento Humano")))
                .andExpect(jsonPath("$.nodos", hasSize(7)))
                .andExpect(jsonPath("$.arcos", hasSize(7)))
                .andExpect(jsonPath("$.nodos[?(@.tipo == 'GATEWAY')].tipoGateway", contains("EXCLUSIVO")))
                .andExpect(jsonPath("$.arcos[*].condicion", containsInAnyOrder(null, null, null, "Sí", "No", null, null)))
                .andExpect(jsonPath("$.advertencias", empty()));

        api.put(editor, "/api/v1/procesos/{id}", publicar(), proceso)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));
    }

    @Test
    void HU04_borradorIncompleto_SeGuardaConAdvertenciasPeroNoSePublica() throws Exception {
        evento("INICIO", laneEmpleado);
        actividad(laneEmpleado, "Radicar solicitud");

        api.get(editor, "/api/v1/procesos/{id}/validacion", proceso)
                .andExpect(jsonPath("$[*].codigo", hasItem("SIN_FIN")))
                .andExpect(jsonPath("$[*].codigo", hasItem("SIN_ENTRADA")));
        api.put(editor, "/api/v1/procesos/{id}", publicar(), proceso)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detalles", hasItem("'Radicar solicitud' no tiene camino de entrada")));
    }

    @Test
    void ProcesoPublicado_CambioQueRompeLaCoherenciaSeRechazaYNoSeGuarda() throws Exception {
        armarVacaciones();
        api.put(editor, "/api/v1/procesos/{id}", publicar(), proceso).andExpect(status().isOk());

        api.post(editor, "/api/v1/lanes/{id}/actividades", Map.of("nombre", "Suelta", "tipoActividad", "TAREA"), laneTH)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Páselo a borrador")));
        api.get(lector, "/api/v1/procesos/{id}/diagrama", proceso).andExpect(jsonPath("$.nodos", hasSize(7)));

        api.put(editor, "/api/v1/actividades/{id}", Map.of("nombre", "Radicar solicitud de vacaciones",
                "tipoActividad", "USUARIO", "laneId", laneEmpleado), radicar).andExpect(status().isOk());
    }

    // ---------- HU-08 a HU-10 Actividades ----------

    @Test
    void HU08_actividadEnSuLaneConPosicionEHistorial() throws Exception {
        api.post(editor, "/api/v1/lanes/{id}/actividades",
                        Map.of("nombre", "Radicar solicitud", "tipoActividad", "USUARIO", "posX", 120, "posY", 60), laneEmpleado)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.laneId").value(laneEmpleado))
                .andExpect(jsonPath("$.poolId").value(pool))
                .andExpect(jsonPath("$.posX").value(120))
                .andExpect(jsonPath("$.tipo").value("ACTIVIDAD"));

        api.get(lector, "/api/v1/procesos/{id}/historial", proceso)
                .andExpect(jsonPath("$.content[0].elemento").value("ACTIVIDAD"))
                .andExpect(jsonPath("$.content[0].detalle", containsString("lane 'Empleado'")));
    }

    @Test
    void HU08_reglasDeCreacion() throws Exception {
        actividad(laneEmpleado, "Radicar solicitud");

        api.post(editor, "/api/v1/lanes/{id}/actividades", Map.of("nombre", "radicar SOLICITUD", "tipoActividad", "TAREA"), laneJefe)
                .andExpect(status().isConflict());
        api.post(editor, "/api/v1/lanes/{id}/actividades", Map.of("tipoActividad", "TAREA"), laneJefe)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.nombre").exists());
        api.post(editor, "/api/v1/lanes/{id}/actividades", Map.of("nombre", "X", "tipoActividad", "VOLAR"), laneJefe)
                .andExpect(status().isBadRequest());
        api.post(editor, "/api/v1/lanes/{id}/actividades", Map.of("nombre", "X", "tipoActividad", "TAREA", "posX", -5), laneJefe)
                .andExpect(status().isBadRequest());
        api.post(lector, "/api/v1/lanes/{id}/actividades", Map.of("nombre", "X", "tipoActividad", "TAREA"), laneJefe)
                .andExpect(status().isForbidden());
        api.post(editor, "/api/v1/lanes/{id}/actividades", Map.of("nombre", "X", "tipoActividad", "TAREA"), 999999)
                .andExpect(status().isNotFound());
    }

    @Test
    void HU09_cambiarDeLaneCambiaElResponsableYConservaLosArcos() throws Exception {
        armarVacaciones();

        api.put(editor, "/api/v1/actividades/{id}", Map.of("nombre", "Registrar rechazo", "tipoActividad", "MANUAL",
                        "laneId", laneTH, "posX", 300, "posY", 200), rechazo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.laneId").value(laneTH))
                .andExpect(jsonPath("$.tipoActividad").value("MANUAL"));

        api.get(lector, "/api/v1/procesos/{id}/diagrama", proceso).andExpect(jsonPath("$.arcos", hasSize(7)));
        api.get(lector, "/api/v1/procesos/{id}/historial", proceso)
                .andExpect(jsonPath("$.content[0].detalle", containsString("responsable: 'Jefe inmediato' → 'Talento Humano'")));
    }

    @Test
    void HU09_noSeMueveAOtroPoolNiSinLaneNiLaEditaElLector() throws Exception {
        long radicarId = actividad(laneEmpleado, "Radicar solicitud");
        long cliente = api.id(api.post(editor, "/api/v1/procesos/{id}/pools", Map.of("nombre", "Cliente", "tipo", "CLIENTE"), proceso), "id");
        long rol = api.id(api.post(admin, "/api/v1/roles-proceso", Map.of("nombre", "Comprador")), "id");
        long laneCliente = api.id(api.post(editor, "/api/v1/pools/{id}/lanes", Map.of("rolProcesoId", rol), cliente), "id");

        api.put(editor, "/api/v1/actividades/{id}", Map.of("nombre", "Radicar solicitud", "tipoActividad", "TAREA",
                "laneId", laneCliente), radicarId).andExpect(status().isConflict());
        api.put(editor, "/api/v1/actividades/{id}", Map.of("nombre", "Radicar solicitud", "tipoActividad", "TAREA"), radicarId)
                .andExpect(status().isBadRequest());
        api.put(lector, "/api/v1/actividades/{id}", Map.of("nombre", "X", "tipoActividad", "TAREA", "laneId", laneEmpleado), radicarId)
                .andExpect(status().isForbidden());
    }

    @Test
    void HU10_eliminarActividad_ArrastraSusArcosYAdvierteLoQueQuedaDesconectado() throws Exception {
        armarVacaciones();

        api.get(editor, "/api/v1/actividades/{id}/impacto", revisar)
                .andExpect(jsonPath("$.arcosQueSeEliminan", hasSize(2)))
                .andExpect(jsonPath("$.advertencias[*].mensaje", hasItem("'Radicar solicitud' no tiene camino de salida")))
                .andExpect(jsonPath("$.advertencias[*].mensaje", hasItem("'¿Aprobada?' no tiene camino de entrada")));

        api.delete(editor, "/api/v1/actividades/{id}", revisar).andExpect(status().isForbidden());
        api.delete(admin, "/api/v1/actividades/{id}", revisar).andExpect(status().isNoContent());

        api.get(lector, "/api/v1/procesos/{id}/diagrama", proceso)
                .andExpect(jsonPath("$.nodos", hasSize(6)))
                .andExpect(jsonPath("$.arcos", hasSize(5)));
        api.get(lector, "/api/v1/procesos/{id}/historial", proceso)
                .andExpect(jsonPath("$.content[0].tipo").value("ELIMINACION"))
                .andExpect(jsonPath("$.content[0].advertencias", hasItem("'Radicar solicitud' no tiene camino de salida")));
        api.get(lector, "/api/v1/actividades/{id}", revisar).andExpect(status().isNotFound());
    }

    @Test
    void HU22_laneConActividadesNoSeElimina() throws Exception {
        actividad(laneEmpleado, "Radicar solicitud");

        api.delete(editor, "/api/v1/lanes/{id}", laneEmpleado)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("reasígnelos")));
    }

    // ---------- HU-11 a HU-13 Arcos ----------

    @Test
    void HU11_reglasDelArco() throws Exception {
        long a = actividad(laneEmpleado, "A");
        long b = actividad(laneJefe, "B");
        long i = evento("INICIO", laneEmpleado);
        long f = evento("FIN", laneEmpleado);
        long cliente = api.id(api.post(editor, "/api/v1/procesos/{id}/pools", Map.of("nombre", "Cliente", "tipo", "CLIENTE"), proceso), "id");
        long eventoCliente = api.id(api.post(editor, "/api/v1/pools/{id}/eventos", Map.of("tipo", "INICIO"), cliente), "id");
        arco(a, b, null);

        api.post(editor, "/api/v1/arcos", Map.of("procesoId", proceso, "origenId", a, "destinoId", a))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("El origen y el destino no pueden ser el mismo elemento"));
        api.post(editor, "/api/v1/arcos", Map.of("procesoId", proceso, "origenId", a, "destinoId", b))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail", containsString("Ya existe un arco")));
        api.post(editor, "/api/v1/arcos", Map.of("procesoId", proceso, "origenId", eventoCliente, "destinoId", a))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail", containsString("se modela como mensaje")));
        api.post(editor, "/api/v1/arcos", Map.of("procesoId", proceso, "origenId", a, "destinoId", i))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Un evento de inicio no puede tener arcos entrantes"));
        api.post(editor, "/api/v1/arcos", Map.of("procesoId", proceso, "origenId", f, "destinoId", a))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Un evento de fin no puede tener arcos salientes"));
        api.post(editor, "/api/v1/arcos", Map.of("procesoId", proceso, "origenId", b, "destinoId", f, "condicion", "x"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Solo los arcos que salen de un gateway llevan condición"));
        api.post(editor, "/api/v1/arcos", Map.of("procesoId", proceso, "origenId", a)).andExpect(status().isBadRequest());
        api.post(lector, "/api/v1/arcos", Map.of("procesoId", proceso, "origenId", b, "destinoId", f)).andExpect(status().isForbidden());
    }

    @Test
    void HU11_idsDelCuerpoDeOtraEmpresa_404() throws Exception {
        long a = actividad(laneEmpleado, "A");
        long procesoB = api.id(api.post(otraEmpresa, "/api/v1/procesos", Map.of("nombre", "Ajeno", "categoria", "X")), "id");
        long poolB = api.id(api.get(otraEmpresa, "/api/v1/procesos/{id}/pools", procesoB), "0.id");
        long nodoB = api.id(api.post(otraEmpresa, "/api/v1/pools/{id}/eventos", Map.of("tipo", "FIN"), poolB), "id");

        api.post(editor, "/api/v1/arcos", Map.of("procesoId", proceso, "origenId", a, "destinoId", nodoB))
                .andExpect(status().isNotFound());
        api.post(otraEmpresa, "/api/v1/arcos", Map.of("procesoId", procesoB, "origenId", a, "destinoId", nodoB))
                .andExpect(status().isNotFound());
        api.post(editor, "/api/v1/arcos", Map.of("procesoId", procesoB, "origenId", a, "destinoId", a))
                .andExpect(status().isNotFound());
    }

    @Test
    void HU12_editarArco_MismasValidacionesYHistorial() throws Exception {
        armarVacaciones();

        api.put(editor, "/api/v1/arcos/{id}", Map.of("procesoId", proceso, "origenId", decision, "destinoId", actualizar,
                        "etiqueta", "Aprobada", "condicion", "diasDisponibles >= diasSolicitados"), arcoSi)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.condicion").value("diasDisponibles >= diasSolicitados"));
        api.put(editor, "/api/v1/arcos/{id}", Map.of("procesoId", proceso, "origenId", decision, "destinoId", rechazo), arcoSi)
                .andExpect(status().isConflict());
        api.put(editor, "/api/v1/arcos/{id}", Map.of("procesoId", proceso, "origenId", fin, "destinoId", rechazo), arcoSi)
                .andExpect(status().isConflict());
        api.put(editor, "/api/v1/arcos/{id}", Map.of("procesoId", proceso + 999, "origenId", decision, "destinoId", actualizar), arcoSi)
                .andExpect(status().isConflict());

        api.get(lector, "/api/v1/procesos/{id}/historial", proceso)
                .andExpect(jsonPath("$.content[0].elemento").value("ARCO"))
                .andExpect(jsonPath("$.content[0].detalle", containsString("diasDisponibles >= diasSolicitados")));
    }

    @Test
    void HU13_eliminarArco_AdvierteElementosSinCaminoYSoloAdministrador() throws Exception {
        armarVacaciones();

        api.get(editor, "/api/v1/arcos/{id}/impacto", arcoNo)
                .andExpect(jsonPath("$.advertencias[*].mensaje", hasItem("'Registrar rechazo' no tiene camino de entrada")))
                .andExpect(jsonPath("$.advertencias[*].codigo", hasItem("GATEWAY_SIN_RAMIFICACION")));
        api.delete(editor, "/api/v1/arcos/{id}", arcoNo).andExpect(status().isForbidden());
        api.delete(admin, "/api/v1/arcos/{id}", arcoNo).andExpect(status().isNoContent());

        api.get(lector, "/api/v1/procesos/{id}/arcos", proceso).andExpect(jsonPath("$", hasSize(6)));
        api.get(lector, "/api/v1/arcos/{id}", arcoNo).andExpect(status().isNotFound());
    }

    // ---------- HU-14 a HU-16 Gateways ----------

    @Test
    void HU14_gatewayDeCadaTipoConSuSimbolo() throws Exception {
        for (String tipo : new String[] {"EXCLUSIVO", "PARALELO", "INCLUSIVO"}) {
            api.post(editor, "/api/v1/lanes/{id}/gateways", Map.of("tipoGateway", tipo), laneJefe)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.gateway.tipoGateway").value(tipo))
                    .andExpect(jsonPath("$.gateway.tipo").value("GATEWAY"));
        }
        api.post(editor, "/api/v1/lanes/{id}/gateways", Map.of("tipoGateway", "COMPLEJO"), laneJefe)
                .andExpect(status().isBadRequest());
    }

    @Test
    void HU14_divergenciaConMenosDeDosSalidasOSinCondiciones_Advierte() throws Exception {
        armarVacaciones();
        api.put(editor, "/api/v1/arcos/{id}", Map.of("procesoId", proceso, "origenId", decision, "destinoId", rechazo), arcoNo)
                .andExpect(status().isOk());

        api.get(editor, "/api/v1/gateways/{id}", decision)
                .andExpect(jsonPath("$.advertencias[*].codigo", contains("CONDICION_FALTANTE")));
    }

    @Test
    void HU15_aParaleloEliminaLasCondiciones_YAExclusivoAdvierteRepetidas() throws Exception {
        armarVacaciones();

        api.put(editor, "/api/v1/gateways/{id}", Map.of("nombre", "Dividir", "tipoGateway", "PARALELO", "laneId", laneJefe), decision)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateway.tipoGateway").value("PARALELO"))
                .andExpect(jsonPath("$.advertencias", empty()));
        api.get(lector, "/api/v1/arcos/{id}", arcoSi).andExpect(jsonPath("$.condicion", nullValue()));
        api.post(editor, "/api/v1/arcos", Map.of("procesoId", proceso, "origenId", decision, "destinoId", fin, "condicion", "x"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail", containsString("gateway paralelo")));

        api.put(editor, "/api/v1/gateways/{id}", Map.of("tipoGateway", "INCLUSIVO"), decision)
                .andExpect(jsonPath("$.advertencias[*].codigo", hasItem("CONDICION_FALTANTE")));
        api.put(editor, "/api/v1/arcos/{id}", Map.of("procesoId", proceso, "origenId", decision, "destinoId", actualizar, "condicion", "aprobada"), arcoSi);
        api.put(editor, "/api/v1/arcos/{id}", Map.of("procesoId", proceso, "origenId", decision, "destinoId", rechazo, "condicion", "APROBADA"), arcoNo);
        api.put(editor, "/api/v1/gateways/{id}", Map.of("tipoGateway", "EXCLUSIVO"), decision)
                .andExpect(jsonPath("$.advertencias[*].codigo", contains("CONDICIONES_NO_EXCLUYENTES")))
                .andExpect(jsonPath("$.advertencias[0].severidad").value("AVISO"));

        api.get(lector, "/api/v1/procesos/{id}/historial", proceso)
                .andExpect(jsonPath("$.content[*].detalle", hasItem(containsString("se eliminaron 2 condiciones"))));
    }

    @Test
    void HU16_eliminarGateway_AdvierteRamasSinDecisionYArrastraSusArcos() throws Exception {
        armarVacaciones();

        api.get(editor, "/api/v1/gateways/{id}/impacto", decision)
                .andExpect(jsonPath("$.arcosQueSeEliminan", hasSize(3)))
                .andExpect(jsonPath("$.advertencias[0].codigo").value("RAMIFICACION_SIN_DECISION"))
                .andExpect(jsonPath("$.advertencias[0].mensaje", containsString("Las 2 ramas")));
        api.delete(editor, "/api/v1/gateways/{id}", decision).andExpect(status().isForbidden());
        api.delete(admin, "/api/v1/gateways/{id}", decision).andExpect(status().isNoContent());

        api.get(lector, "/api/v1/procesos/{id}/diagrama", proceso)
                .andExpect(jsonPath("$.arcos", hasSize(4)))
                .andExpect(jsonPath("$.nodos[*].tipo", not(hasItem("GATEWAY"))));
    }

    // ---------- Eventos ----------

    @Test
    void Eventos_reglasDeInicioYFin() throws Exception {
        long correo = api.id(api.post(editor, "/api/v1/procesos/{id}/pools", Map.of("nombre", "Correo", "tipo", "SISTEMA_EXTERNO"), proceso), "id");
        armarVacaciones();

        api.post(editor, "/api/v1/pools/{id}/eventos", Map.of("tipo", "INICIO"), correo).andExpect(status().isConflict());
        api.put(editor, "/api/v1/eventos/{id}", Map.of("tipo", "INICIO"), fin).andExpect(status().isConflict());
        api.put(editor, "/api/v1/eventos/{id}", Map.of("tipo", "FIN"), inicio).andExpect(status().isConflict());
        api.post(editor, "/api/v1/pools/{id}/eventos", Map.of("tipo", "MEDIO"), pool).andExpect(status().isBadRequest());
        api.delete(admin, "/api/v1/eventos/{id}", inicio).andExpect(status().isNoContent());
        api.get(editor, "/api/v1/procesos/{id}/validacion", proceso)
                .andExpect(jsonPath("$[*].codigo", hasItem("SIN_INICIO")));
    }

    // ---------- Aislamiento ----------

    @Test
    void ElementosDeOtraEmpresa_404EnTodasLasOperaciones() throws Exception {
        armarVacaciones();

        api.get(otraEmpresa, "/api/v1/actividades/{id}", radicar).andExpect(status().isNotFound());
        api.put(otraEmpresa, "/api/v1/actividades/{id}", Map.of("nombre", "X", "tipoActividad", "TAREA", "laneId", laneEmpleado), radicar)
                .andExpect(status().isNotFound());
        api.delete(otraEmpresa, "/api/v1/actividades/{id}", radicar).andExpect(status().isNotFound());
        api.get(otraEmpresa, "/api/v1/gateways/{id}", decision).andExpect(status().isNotFound());
        api.delete(otraEmpresa, "/api/v1/gateways/{id}", decision).andExpect(status().isNotFound());
        api.get(otraEmpresa, "/api/v1/arcos/{id}", arcoSi).andExpect(status().isNotFound());
        api.delete(otraEmpresa, "/api/v1/arcos/{id}", arcoSi).andExpect(status().isNotFound());
        api.get(otraEmpresa, "/api/v1/eventos/{id}", inicio).andExpect(status().isNotFound());
        api.get(otraEmpresa, "/api/v1/procesos/{id}/arcos", proceso).andExpect(status().isNotFound());
        api.get(otraEmpresa, "/api/v1/procesos/{id}/validacion", proceso).andExpect(status().isNotFound());
        api.post(otraEmpresa, "/api/v1/lanes/{id}/actividades", Map.of("nombre", "X", "tipoActividad", "TAREA"), laneEmpleado)
                .andExpect(status().isNotFound());
    }
}

package co.edu.javeriana.bpmn.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.gestion.entity.EstadoProceso;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.modelado.dto.Advertencia;
import co.edu.javeriana.bpmn.modelado.entity.Arco;
import co.edu.javeriana.bpmn.modelado.entity.DireccionMensaje;
import co.edu.javeriana.bpmn.modelado.entity.Mensaje;
import co.edu.javeriana.bpmn.modelado.entity.VarianteEvento;
import co.edu.javeriana.bpmn.modelado.entity.Lane;
import co.edu.javeriana.bpmn.modelado.entity.NodoFlujo;
import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoGateway;
import co.edu.javeriana.bpmn.modelado.entity.TipoNodo;
import co.edu.javeriana.bpmn.modelado.entity.TipoPool;
import co.edu.javeriana.bpmn.modelado.repository.ArcoRepository;
import co.edu.javeriana.bpmn.modelado.repository.MensajeRepository;
import co.edu.javeriana.bpmn.modelado.repository.NodoFlujoRepository;
import co.edu.javeriana.bpmn.modelado.repository.PoolRepository;
import co.edu.javeriana.bpmn.modelado.service.impl.ValidadorDeProcesoServiceImpl;

/** Cada regla de coherencia del diagrama, con nodos y arcos armados en memoria. */
/**
 * Pruebas unitarias del validador de coherencia del diagrama BPMN. Los nodos y arcos se arman en memoria y
 * los repositorios son mocks de Mockito, así que no hay base de datos ni Spring.
 */
@ExtendWith(MockitoExtension.class)
// LENIENT: no falla si algún when() preparado en @BeforeEach no se usa en una prueba concreta.
@MockitoSettings(strictness = Strictness.LENIENT)
class ValidadorDeProcesoServiceImplTest {

    // Dobles de los repositorios que consulta el validador.
    @Mock
    private NodoFlujoRepository nodoRepository;

    @Mock
    private ArcoRepository arcoRepository;

    @Mock
    private PoolRepository poolRepository;

    @Mock
    private MensajeRepository mensajeRepository;

    // Objeto bajo prueba, con los mocks inyectados.
    @InjectMocks
    private ValidadorDeProcesoServiceImpl validador;

    // Datos base: un proceso en borrador, un pool propietario y una lane.
    private final Proceso proceso = Proceso.builder().id(1L).estado(EstadoProceso.BORRADOR).build();
    private final Pool pool = Pool.builder().id(10L).nombre("Empresa").tipo(TipoPool.PROPIETARIO).build();
    private final Lane lane = Lane.builder().id(20L).build();
    // Listas que los mocks devuelven; cada prueba las llena con nodos y arcos.
    private final List<NodoFlujo> nodos = new ArrayList<>();
    private final List<Arco> arcos = new ArrayList<>();
    private long ids = 100;

    // Antes de cada prueba: los repositorios devuelven las listas de nodos, arcos y el pool.
    @BeforeEach
    void repositorios() {
        when(nodoRepository.listarActivosDelProceso(1L)).thenReturn(nodos);
        when(arcoRepository.listarActivosDelProceso(1L)).thenReturn(arcos);
        when(poolRepository.findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(1L)).thenReturn(List.of(pool));
    }

    // Auxiliar: crea un nodo y lo agrega al diagrama; actividades y gateways reciben lane.
    private NodoFlujo nodo(TipoNodo tipo, String nombre) {
        NodoFlujo n = NodoFlujo.builder().id(ids++).proceso(proceso).pool(pool).tipo(tipo).nombre(nombre)
                .lane(tipo == TipoNodo.ACTIVIDAD || tipo == TipoNodo.GATEWAY ? lane : null).build();
        nodos.add(n);
        return n;
    }

    // Auxiliar: crea un gateway del tipo indicado.
    private NodoFlujo gateway(TipoGateway tipo) {
        NodoFlujo g = nodo(TipoNodo.GATEWAY, "¿Aprobada?");
        g.setTipoGateway(tipo);
        return g;
    }

    // Auxiliar: crea un arco entre dos nodos (con condición opcional).
    private Arco arco(NodoFlujo origen, NodoFlujo destino, String condicion) {
        Arco a = Arco.builder().id(ids++).proceso(proceso).origen(origen).destino(destino).condicion(condicion).build();
        arcos.add(a);
        return a;
    }

    // Auxiliar: ejecuta la revisión y devuelve solo los códigos de las advertencias.
    private List<String> codigos() {
        return validador.revisar(proceso).stream().map(Advertencia::codigo).toList();
    }

    /** inicio → radicar → ¿aprobada? (X) → sí: aprobar → fin / no: rechazar → fin */
    // Auxiliar: arma un diagrama válido. Los índices de nodos y arcos se usan luego para romperlo.
    private void diagramaCoherente() {
        NodoFlujo inicio = nodo(TipoNodo.EVENTO_INICIO, "Inicio");
        NodoFlujo radicar = nodo(TipoNodo.ACTIVIDAD, "Radicar");
        NodoFlujo decision = gateway(TipoGateway.EXCLUSIVO);
        NodoFlujo aprobar = nodo(TipoNodo.ACTIVIDAD, "Aprobar");
        NodoFlujo rechazar = nodo(TipoNodo.ACTIVIDAD, "Rechazar");
        NodoFlujo fin = nodo(TipoNodo.EVENTO_FIN, "Fin");
        arco(inicio, radicar, null);
        arco(radicar, decision, null);
        arco(decision, aprobar, "Sí");
        arco(decision, rechazar, "No");
        arco(aprobar, fin, null);
        arco(rechazar, fin, null);
    }

    // Escenario: no hay ningún elemento.
    // Esperado: solo el error DIAGRAMA_VACIO.
    @Test
    void Validador_diagramaVacio_EsError() {
        assertThat(codigos()).containsExactly("DIAGRAMA_VACIO");
    }

    // Escenario: diagrama completo y correcto.
    // Esperado: ninguna advertencia y se puede publicar (no lanza excepción).
    @Test
    void Validador_diagramaCoherente_SinAdvertencias() {
        diagramaCoherente();

        assertThat(validador.revisar(proceso)).isEmpty();
        assertThatCode(() -> validador.exigirCoherenciaParaPublicar(proceso)).doesNotThrowAnyException();
    }

    // Escenario: dos actividades unidas sin eventos.
    // Esperado: faltan inicio y fin, y la primera no tiene entrada y la segunda no tiene salida.
    @Test
    void Validador_sinInicioNiFin_ErroresDelPool() {
        NodoFlujo a = nodo(TipoNodo.ACTIVIDAD, "A");
        NodoFlujo b = nodo(TipoNodo.ACTIVIDAD, "B");
        arco(a, b, null);

        assertThat(codigos()).contains("SIN_INICIO", "SIN_FIN", "SIN_ENTRADA", "SIN_SALIDA");
    }

    // Escenario: una actividad sin arcos.
    // Esperado: avisos de falta de entrada y de salida con su nombre.
    @Test
    void Validador_elementoSuelto_SinEntradaNiSalida() {
        diagramaCoherente();
        nodo(TipoNodo.ACTIVIDAD, "Suelta");

        List<Advertencia> advertencias = validador.revisar(proceso);
        assertThat(advertencias).extracting(Advertencia::mensaje)
                .contains("'Suelta' no tiene camino de entrada", "'Suelta' no tiene camino de salida");
    }

    // Escenario: dos actividades que solo se conectan entre sí.
    // Esperado: ambas son INALCANZABLES desde el inicio.
    @Test
    void Validador_cicloDesconectadoDelInicio_Inalcanzable() {
        diagramaCoherente();
        NodoFlujo x = nodo(TipoNodo.ACTIVIDAD, "X");
        NodoFlujo y = nodo(TipoNodo.ACTIVIDAD, "Y");
        arco(x, y, null);
        arco(y, x, null);

        assertThat(validador.revisar(proceso)).extracting(Advertencia::codigo).containsOnly("INALCANZABLE");
    }

    // Escenario: gateway exclusivo con una entrada y una salida.
    // Esperado: GATEWAY_SIN_RAMIFICACION.
    @Test
    void Validador_gatewayConUnaSolaSalida_SinRamificacion() {
        NodoFlujo inicio = nodo(TipoNodo.EVENTO_INICIO, "Inicio");
        NodoFlujo g = gateway(TipoGateway.EXCLUSIVO);
        NodoFlujo fin = nodo(TipoNodo.EVENTO_FIN, "Fin");
        arco(inicio, g, null);
        arco(g, fin, null);

        assertThat(codigos()).containsExactly("GATEWAY_SIN_RAMIFICACION");
    }

    // Escenario: un gateway paralelo que divide y otro que une.
    // Esperado: sin advertencias; un gateway que une no necesita dos salidas.
    @Test
    void Validador_gatewayDeConvergencia_NoNecesitaDosSalidas() {
        NodoFlujo inicio = nodo(TipoNodo.EVENTO_INICIO, "Inicio");
        NodoFlujo division = gateway(TipoGateway.PARALELO);
        NodoFlujo a = nodo(TipoNodo.ACTIVIDAD, "A");
        NodoFlujo b = nodo(TipoNodo.ACTIVIDAD, "B");
        NodoFlujo union = gateway(TipoGateway.PARALELO);
        NodoFlujo fin = nodo(TipoNodo.EVENTO_FIN, "Fin");
        arco(inicio, division, null);
        arco(division, a, null);
        arco(division, b, null);
        arco(a, union, null);
        arco(b, union, null);
        arco(union, fin, null);

        assertThat(validador.revisar(proceso)).isEmpty();
    }

    // Escenario: se quita la condición de un arco del gateway exclusivo.
    // Esperado: CONDICION_FALTANTE.
    @Test
    void Validador_exclusivoSinCondicion_Error() {
        diagramaCoherente();
        arcos.get(3).setCondicion(null);

        assertThat(codigos()).containsExactly("CONDICION_FALTANTE");
    }

    // Escenario: el gateway pasa a inclusivo y un arco queda con condición en blanco.
    // Esperado: CONDICION_FALTANTE.
    @Test
    void Validador_inclusivoSinCondicion_Error() {
        diagramaCoherente();
        nodos.get(2).setTipoGateway(TipoGateway.INCLUSIVO);
        arcos.get(2).setCondicion("  ");

        assertThat(codigos()).containsExactly("CONDICION_FALTANTE");
    }

    // Escenario: dos arcos con la misma condición (ignorando espacios y mayúsculas).
    // Esperado: un aviso CONDICIONES_NO_EXCLUYENTES que no es error y no impide publicar.
    @Test
    void Validador_exclusivoConCondicionesRepetidas_AvisoNoBloquea() {
        diagramaCoherente();
        arcos.get(3).setCondicion(" sí ");

        List<Advertencia> advertencias = validador.revisar(proceso);
        assertThat(advertencias).singleElement().satisfies(a -> {
            assertThat(a.codigo()).isEqualTo("CONDICIONES_NO_EXCLUYENTES");
            assertThat(a.esError()).isFalse();
        });
        assertThatCode(() -> validador.exigirCoherenciaParaPublicar(proceso)).doesNotThrowAnyException();
    }

    // Escenario: gateway paralelo sin condiciones.
    // Esperado: es correcto, sin advertencias.
    @Test
    void Validador_paraleloSinCondiciones_EsCorrecto() {
        diagramaCoherente();
        nodos.get(2).setTipoGateway(TipoGateway.PARALELO);
        arcos.get(2).setCondicion(null);
        arcos.get(3).setCondicion(null);

        assertThat(validador.revisar(proceso)).isEmpty();
    }

    // Escenario: una actividad sin lane.
    // Esperado: ACTIVIDAD_SIN_LANE.
    @Test
    void Validador_actividadSinLane_Error() {
        diagramaCoherente();
        nodos.get(1).setLane(null);

        assertThat(codigos()).containsExactly("ACTIVIDAD_SIN_LANE");
    }

    // Escenario: un pool caja negra contiene una actividad.
    // Esperado: CAJA_NEGRA_CON_ELEMENTOS.
    @Test
    void Validador_poolCajaNegraConElementos_Error() {
        pool.setCajaNegra(true);
        nodo(TipoNodo.ACTIVIDAD, "Interna");

        assertThat(codigos()).containsExactly("CAJA_NEGRA_CON_ELEMENTOS");
    }

    // ---------- Mensajes (HU-25 a HU-28) ----------

    // Datos para las pruebas de mensajes: un segundo pool y la lista de mensajes.
    private final Pool proveedor = Pool.builder().id(11L).nombre("Proveedor").tipo(TipoPool.PROVEEDOR).build();
    private final List<Mensaje> mensajes = new ArrayList<>();

    // Auxiliar: los mocks devuelven dos pools y la lista de mensajes.
    private void conProveedor() {
        when(poolRepository.findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(1L)).thenReturn(List.of(pool, proveedor));
        when(mensajeRepository.listarActivosDelProceso(1L)).thenReturn(mensajes);
    }

    // Auxiliar: crea un mensaje de envío (throw) hacia el proveedor.
    private Mensaje envio(String nombre, String clave) {
        NodoFlujo evento = nodo(TipoNodo.MESSAGE_THROW, nombre);
        evento.setVariante(VarianteEvento.FIN);
        Mensaje m = Mensaje.builder().id(ids++).nodo(evento).direccion(DireccionMensaje.ENVIO).nombre(nombre)
                .poolDestino(proveedor).claveCorrelacion(clave).build();
        mensajes.add(m);
        return m;
    }

    // Auxiliar: crea un mensaje de recepción (catch) en el pool proveedor.
    private Mensaje recepcion(String nombre, VarianteEvento variante, String clave, boolean externo) {
        NodoFlujo evento = NodoFlujo.builder().id(ids++).proceso(proceso).pool(proveedor).tipo(TipoNodo.MESSAGE_CATCH)
                .nombre(nombre).variante(variante).build();
        nodos.add(evento);
        Mensaje m = Mensaje.builder().id(ids++).nodo(evento).direccion(DireccionMensaje.RECEPCION).nombre(nombre)
                .claveCorrelacion(clave).origenExterno(externo).build();
        mensajes.add(m);
        return m;
    }

    // Auxiliar: devuelve solo las advertencias relacionadas con mensajes y correlación.
    private List<String> codigosDeMensajes() {
        return validador.revisar(proceso).stream().map(Advertencia::codigo)
                .filter(c -> c.startsWith("MENSAJE") || c.startsWith("CATCH") || c.startsWith("CORRELACION")).toList();
    }

    // Escenario: un envío sin recepción en el destino.
    // Esperado: aviso MENSAJE_SIN_RECEPTOR.
    @Test
    void Validador_throwSinCatchEnElDestino_Aviso() {
        conProveedor();
        envio("OrdenDeCompra", null);

        assertThat(codigosDeMensajes()).containsExactly("MENSAJE_SIN_RECEPTOR");
    }

    // Escenario: una recepción sin emisor ni origen externo.
    // Esperado: error CATCH_SIN_EMISOR.
    @Test
    void Validador_catchSinThrowNiOrigenExterno_Error() {
        conProveedor();
        recepcion("Pago", VarianteEvento.INICIO, null, false);

        assertThat(codigosDeMensajes()).containsExactly("CATCH_SIN_EMISOR");
    }

    // Escenario: recepción marcada como origen externo.
    // Esperado: no necesita emisor.
    @Test
    void Validador_catchDeOrigenExterno_NoNecesitaEmisor() {
        conProveedor();
        recepcion("Pago", VarianteEvento.INICIO, null, true);

        assertThat(codigosDeMensajes()).isEmpty();
    }

    // Escenario: envío y recepción con la misma clave (distinta capitalización y espacios).
    // Esperado: sin advertencias.
    @Test
    void Validador_parThrowCatchConLaMismaClave_SinProblemas() {
        conProveedor();
        envio("Orden", "numeroOrden");
        recepcion("orden", VarianteEvento.INTERMEDIO, " NUMEROORDEN ", false);

        assertThat(codigosDeMensajes()).isEmpty();
    }

    // Escenario: claves de correlación distintas.
    // Esperado: CORRELACION_INCOHERENTE.
    @Test
    void Validador_claveDistintaEntreThrowYCatch_Incoherente() {
        conProveedor();
        envio("Orden", "numeroOrden");
        recepcion("Orden", VarianteEvento.INTERMEDIO, "nit", false);

        assertThat(codigosDeMensajes()).containsExactly("CORRELACION_INCOHERENTE");
    }

    // Escenario: recepción intermedia sin clave de correlación.
    // Esperado: aviso CORRELACION_FALTANTE.
    @Test
    void Validador_catchIntermedioSinClave_Aviso() {
        conProveedor();
        envio("Orden", null);
        recepcion("Orden", VarianteEvento.INTERMEDIO, null, false);

        assertThat(codigosDeMensajes()).containsExactly("CORRELACION_FALTANTE");
    }

    // Escenario: dos envíos con el mismo nombre y clave.
    // Esperado: CORRELACION_AMBIGUA para cada uno.
    @Test
    void Validador_dosEnviosConMismoNombreYClave_Ambiguos() {
        conProveedor();
        envio("Pago", "factura");
        envio("Pago", "FACTURA");
        recepcion("Pago", VarianteEvento.INICIO, "factura", false);

        assertThat(codigosDeMensajes()).containsExactly("CORRELACION_AMBIGUA", "CORRELACION_AMBIGUA");
    }

    // Escenario: el pool destino se elimina; luego el destino es el mismo pool de origen.
    // Esperado: MENSAJE_SIN_DESTINO y después MENSAJE_MISMO_POOL.
    @Test
    void Validador_destinoEliminadoOMismoPool_Error() {
        conProveedor();
        Mensaje m = envio("Orden", null);
        proveedor.setActivo(false);
        assertThat(codigosDeMensajes()).containsExactly("MENSAJE_SIN_DESTINO");

        proveedor.setActivo(true);
        m.setPoolDestino(pool);
        assertThat(codigosDeMensajes()).contains("MENSAJE_MISMO_POOL");
    }

    // Escenario: simular la eliminación de una actividad y sus arcos antes de hacerla.
    // Esperado: el gateway queda con una sola rama (GATEWAY_SIN_RAMIFICACION).
    @Test
    void Validador_revisarSin_SimulaEliminarUnaActividadYSusArcos() {
        diagramaCoherente();
        NodoFlujo aprobar = nodos.get(3);
        Set<Long> arcosDeAprobar = Set.of(arcos.get(2).getId(), arcos.get(4).getId());

        List<Advertencia> despues = validador.revisarSin(proceso, Set.of(aprobar.getId()), arcosDeAprobar);

        assertThat(despues).extracting(Advertencia::codigo).containsExactly("GATEWAY_SIN_RAMIFICACION");
    }

    // Escenario: proceso publicado con una actividad suelta.
    // Esperado: ReglaDeNegocioException que pide pasarlo a borrador, con detalles.
    @Test
    void Validador_procesoPublicadoConErrores_RechazaElCambio() {
        proceso.setEstado(EstadoProceso.PUBLICADO);
        nodo(TipoNodo.ACTIVIDAD, "Suelta");

        assertThatThrownBy(() -> validador.exigirCoherenciaSiPublicado(proceso))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("Páselo a borrador")
                .satisfies(e -> assertThat(((ReglaDeNegocioException) e).getDetalles()).isNotEmpty());
    }

    // Escenario: el mismo problema en un borrador.
    // Esperado: no bloquea los cambios, pero sí impide publicar.
    @Test
    void Validador_procesoEnBorradorConErrores_NoBloquea() {
        nodo(TipoNodo.ACTIVIDAD, "Suelta");

        assertThatCode(() -> validador.exigirCoherenciaSiPublicado(proceso)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validador.exigirCoherenciaParaPublicar(proceso))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("No se puede publicar");
    }

    // Escenario: arcos que entran a un inicio y salen de un fin.
    // Esperado: INICIO_CON_ENTRADAS y FIN_CON_SALIDAS.
    @Test
    void Validador_eventosDeInicioConEntradaYFinConSalida_Errores() {
        NodoFlujo inicio = nodo(TipoNodo.EVENTO_INICIO, "Inicio");
        NodoFlujo fin = nodo(TipoNodo.EVENTO_FIN, "Fin");
        arco(inicio, fin, null);
        arco(fin, inicio, null);

        assertThat(codigos()).contains("INICIO_CON_ENTRADAS", "FIN_CON_SALIDAS");
    }
}

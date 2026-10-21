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
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ValidadorDeProcesoServiceImplTest {

    @Mock
    private NodoFlujoRepository nodoRepository;

    @Mock
    private ArcoRepository arcoRepository;

    @Mock
    private PoolRepository poolRepository;

    @Mock
    private MensajeRepository mensajeRepository;

    @InjectMocks
    private ValidadorDeProcesoServiceImpl validador;

    private final Proceso proceso = Proceso.builder().id(1L).estado(EstadoProceso.BORRADOR).build();
    private final Pool pool = Pool.builder().id(10L).nombre("Empresa").tipo(TipoPool.PROPIETARIO).build();
    private final Lane lane = Lane.builder().id(20L).build();
    private final List<NodoFlujo> nodos = new ArrayList<>();
    private final List<Arco> arcos = new ArrayList<>();
    private long ids = 100;

    @BeforeEach
    void repositorios() {
        when(nodoRepository.listarActivosDelProceso(1L)).thenReturn(nodos);
        when(arcoRepository.listarActivosDelProceso(1L)).thenReturn(arcos);
        when(poolRepository.findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(1L)).thenReturn(List.of(pool));
    }

    private NodoFlujo nodo(TipoNodo tipo, String nombre) {
        NodoFlujo n = NodoFlujo.builder().id(ids++).proceso(proceso).pool(pool).tipo(tipo).nombre(nombre)
                .lane(tipo == TipoNodo.ACTIVIDAD || tipo == TipoNodo.GATEWAY ? lane : null).build();
        nodos.add(n);
        return n;
    }

    private NodoFlujo gateway(TipoGateway tipo) {
        NodoFlujo g = nodo(TipoNodo.GATEWAY, "¿Aprobada?");
        g.setTipoGateway(tipo);
        return g;
    }

    private Arco arco(NodoFlujo origen, NodoFlujo destino, String condicion) {
        Arco a = Arco.builder().id(ids++).proceso(proceso).origen(origen).destino(destino).condicion(condicion).build();
        arcos.add(a);
        return a;
    }

    private List<String> codigos() {
        return validador.revisar(proceso).stream().map(Advertencia::codigo).toList();
    }

    /** inicio → radicar → ¿aprobada? (X) → sí: aprobar → fin / no: rechazar → fin */
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

    @Test
    void Validador_diagramaVacio_EsError() {
        assertThat(codigos()).containsExactly("DIAGRAMA_VACIO");
    }

    @Test
    void Validador_diagramaCoherente_SinAdvertencias() {
        diagramaCoherente();

        assertThat(validador.revisar(proceso)).isEmpty();
        assertThatCode(() -> validador.exigirCoherenciaParaPublicar(proceso)).doesNotThrowAnyException();
    }

    @Test
    void Validador_sinInicioNiFin_ErroresDelPool() {
        NodoFlujo a = nodo(TipoNodo.ACTIVIDAD, "A");
        NodoFlujo b = nodo(TipoNodo.ACTIVIDAD, "B");
        arco(a, b, null);

        assertThat(codigos()).contains("SIN_INICIO", "SIN_FIN", "SIN_ENTRADA", "SIN_SALIDA");
    }

    @Test
    void Validador_elementoSuelto_SinEntradaNiSalida() {
        diagramaCoherente();
        nodo(TipoNodo.ACTIVIDAD, "Suelta");

        List<Advertencia> advertencias = validador.revisar(proceso);
        assertThat(advertencias).extracting(Advertencia::mensaje)
                .contains("'Suelta' no tiene camino de entrada", "'Suelta' no tiene camino de salida");
    }

    @Test
    void Validador_cicloDesconectadoDelInicio_Inalcanzable() {
        diagramaCoherente();
        NodoFlujo x = nodo(TipoNodo.ACTIVIDAD, "X");
        NodoFlujo y = nodo(TipoNodo.ACTIVIDAD, "Y");
        arco(x, y, null);
        arco(y, x, null);

        assertThat(validador.revisar(proceso)).extracting(Advertencia::codigo).containsOnly("INALCANZABLE");
    }

    @Test
    void Validador_gatewayConUnaSolaSalida_SinRamificacion() {
        NodoFlujo inicio = nodo(TipoNodo.EVENTO_INICIO, "Inicio");
        NodoFlujo g = gateway(TipoGateway.EXCLUSIVO);
        NodoFlujo fin = nodo(TipoNodo.EVENTO_FIN, "Fin");
        arco(inicio, g, null);
        arco(g, fin, null);

        assertThat(codigos()).containsExactly("GATEWAY_SIN_RAMIFICACION");
    }

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

    @Test
    void Validador_exclusivoSinCondicion_Error() {
        diagramaCoherente();
        arcos.get(3).setCondicion(null);

        assertThat(codigos()).containsExactly("CONDICION_FALTANTE");
    }

    @Test
    void Validador_inclusivoSinCondicion_Error() {
        diagramaCoherente();
        nodos.get(2).setTipoGateway(TipoGateway.INCLUSIVO);
        arcos.get(2).setCondicion("  ");

        assertThat(codigos()).containsExactly("CONDICION_FALTANTE");
    }

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

    @Test
    void Validador_paraleloSinCondiciones_EsCorrecto() {
        diagramaCoherente();
        nodos.get(2).setTipoGateway(TipoGateway.PARALELO);
        arcos.get(2).setCondicion(null);
        arcos.get(3).setCondicion(null);

        assertThat(validador.revisar(proceso)).isEmpty();
    }

    @Test
    void Validador_actividadSinLane_Error() {
        diagramaCoherente();
        nodos.get(1).setLane(null);

        assertThat(codigos()).containsExactly("ACTIVIDAD_SIN_LANE");
    }

    @Test
    void Validador_poolCajaNegraConElementos_Error() {
        pool.setCajaNegra(true);
        nodo(TipoNodo.ACTIVIDAD, "Interna");

        assertThat(codigos()).containsExactly("CAJA_NEGRA_CON_ELEMENTOS");
    }

    // ---------- Mensajes (HU-25 a HU-28) ----------

    private final Pool proveedor = Pool.builder().id(11L).nombre("Proveedor").tipo(TipoPool.PROVEEDOR).build();
    private final List<Mensaje> mensajes = new ArrayList<>();

    private void conProveedor() {
        when(poolRepository.findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(1L)).thenReturn(List.of(pool, proveedor));
        when(mensajeRepository.listarActivosDelProceso(1L)).thenReturn(mensajes);
    }

    private Mensaje envio(String nombre, String clave) {
        NodoFlujo evento = nodo(TipoNodo.MESSAGE_THROW, nombre);
        evento.setVariante(VarianteEvento.FIN);
        Mensaje m = Mensaje.builder().id(ids++).nodo(evento).direccion(DireccionMensaje.ENVIO).nombre(nombre)
                .poolDestino(proveedor).claveCorrelacion(clave).build();
        mensajes.add(m);
        return m;
    }

    private Mensaje recepcion(String nombre, VarianteEvento variante, String clave, boolean externo) {
        NodoFlujo evento = NodoFlujo.builder().id(ids++).proceso(proceso).pool(proveedor).tipo(TipoNodo.MESSAGE_CATCH)
                .nombre(nombre).variante(variante).build();
        nodos.add(evento);
        Mensaje m = Mensaje.builder().id(ids++).nodo(evento).direccion(DireccionMensaje.RECEPCION).nombre(nombre)
                .claveCorrelacion(clave).origenExterno(externo).build();
        mensajes.add(m);
        return m;
    }

    private List<String> codigosDeMensajes() {
        return validador.revisar(proceso).stream().map(Advertencia::codigo)
                .filter(c -> c.startsWith("MENSAJE") || c.startsWith("CATCH") || c.startsWith("CORRELACION")).toList();
    }

    @Test
    void Validador_throwSinCatchEnElDestino_Aviso() {
        conProveedor();
        envio("OrdenDeCompra", null);

        assertThat(codigosDeMensajes()).containsExactly("MENSAJE_SIN_RECEPTOR");
    }

    @Test
    void Validador_catchSinThrowNiOrigenExterno_Error() {
        conProveedor();
        recepcion("Pago", VarianteEvento.INICIO, null, false);

        assertThat(codigosDeMensajes()).containsExactly("CATCH_SIN_EMISOR");
    }

    @Test
    void Validador_catchDeOrigenExterno_NoNecesitaEmisor() {
        conProveedor();
        recepcion("Pago", VarianteEvento.INICIO, null, true);

        assertThat(codigosDeMensajes()).isEmpty();
    }

    @Test
    void Validador_parThrowCatchConLaMismaClave_SinProblemas() {
        conProveedor();
        envio("Orden", "numeroOrden");
        recepcion("orden", VarianteEvento.INTERMEDIO, " NUMEROORDEN ", false);

        assertThat(codigosDeMensajes()).isEmpty();
    }

    @Test
    void Validador_claveDistintaEntreThrowYCatch_Incoherente() {
        conProveedor();
        envio("Orden", "numeroOrden");
        recepcion("Orden", VarianteEvento.INTERMEDIO, "nit", false);

        assertThat(codigosDeMensajes()).containsExactly("CORRELACION_INCOHERENTE");
    }

    @Test
    void Validador_catchIntermedioSinClave_Aviso() {
        conProveedor();
        envio("Orden", null);
        recepcion("Orden", VarianteEvento.INTERMEDIO, null, false);

        assertThat(codigosDeMensajes()).containsExactly("CORRELACION_FALTANTE");
    }

    @Test
    void Validador_dosEnviosConMismoNombreYClave_Ambiguos() {
        conProveedor();
        envio("Pago", "factura");
        envio("Pago", "FACTURA");
        recepcion("Pago", VarianteEvento.INICIO, "factura", false);

        assertThat(codigosDeMensajes()).containsExactly("CORRELACION_AMBIGUA", "CORRELACION_AMBIGUA");
    }

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

    @Test
    void Validador_revisarSin_SimulaEliminarUnaActividadYSusArcos() {
        diagramaCoherente();
        NodoFlujo aprobar = nodos.get(3);
        Set<Long> arcosDeAprobar = Set.of(arcos.get(2).getId(), arcos.get(4).getId());

        List<Advertencia> despues = validador.revisarSin(proceso, Set.of(aprobar.getId()), arcosDeAprobar);

        assertThat(despues).extracting(Advertencia::codigo).containsExactly("GATEWAY_SIN_RAMIFICACION");
    }

    @Test
    void Validador_procesoPublicadoConErrores_RechazaElCambio() {
        proceso.setEstado(EstadoProceso.PUBLICADO);
        nodo(TipoNodo.ACTIVIDAD, "Suelta");

        assertThatThrownBy(() -> validador.exigirCoherenciaSiPublicado(proceso))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("Páselo a borrador")
                .satisfies(e -> assertThat(((ReglaDeNegocioException) e).getDetalles()).isNotEmpty());
    }

    @Test
    void Validador_procesoEnBorradorConErrores_NoBloquea() {
        nodo(TipoNodo.ACTIVIDAD, "Suelta");

        assertThatCode(() -> validador.exigirCoherenciaSiPublicado(proceso)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validador.exigirCoherenciaParaPublicar(proceso))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("No se puede publicar");
    }

    @Test
    void Validador_eventosDeInicioConEntradaYFinConSalida_Errores() {
        NodoFlujo inicio = nodo(TipoNodo.EVENTO_INICIO, "Inicio");
        NodoFlujo fin = nodo(TipoNodo.EVENTO_FIN, "Fin");
        arco(inicio, fin, null);
        arco(fin, inicio, null);

        assertThat(codigos()).contains("INICIO_CON_ENTRADAS", "FIN_CON_SALIDAS");
    }
}

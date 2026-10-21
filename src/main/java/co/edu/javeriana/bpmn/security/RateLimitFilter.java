package co.edu.javeriana.bpmn.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import co.edu.javeriana.bpmn.common.exception.CodigoError;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limita los intentos de inicio de sesión (HU-03) y de registro de empresas (HU-01) por IP con un cubo de
 * fichas: BCrypt hace la fuerza bruta lenta, no imposible. Pasado el límite responde 429 con Retry-After y el
 * mismo ProblemDetail del resto de la API. El resto de endpoints no pasa por aquí.
 *
 * Los cubos viven en memoria: con varias instancias detrás de un balanceador cada una contaría por su cuenta.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    // Únicas rutas con límite de intentos.
    static final String RUTA_LOGIN = "/api/v1/auth/login";
    static final String RUTA_REGISTRO = "/api/v1/empresas";

    // Un cubo por combinación ruta + IP. ConcurrentHashMap porque varias peticiones llegan a la vez.
    private final Map<String, Bucket> cubos = new ConcurrentHashMap<>();
    private final EscritorDeProblemDetail escritor;
    // Límite de cada ruta, tomado de las propiedades app.rate-limit.*.
    private final Limite login;
    private final Limite registro;

    /** Capacidad del cubo y cada cuánto se recarga completo. */
    public record Limite(long capacidad, Duration periodo) {
    }

    /**
     * @param escritor genera la respuesta 429
     * @param login límite del inicio de sesión
     * @param registro límite del registro de empresas
     */
    public RateLimitFilter(EscritorDeProblemDetail escritor, Limite login, Limite registro) {
        this.escritor = escritor;
        this.login = login;
        this.registro = registro;
    }

    /** El filtro se omite en toda petición que no tenga límite. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return limiteDe(request) == null;
    }

    /** Cada intento consume una ficha del cubo; si no queda ninguna se responde 429. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Se obtiene (o se crea) el cubo de esta ruta e IP y se intenta consumir una ficha.
        Limite limite = limiteDe(request);
        Bucket cubo = cubos.computeIfAbsent(request.getRequestURI() + "|" + request.getRemoteAddr(), clave -> nuevoCubo(limite));
        ConsumptionProbe sonda = cubo.tryConsumeAndReturnRemaining(1);

        // 2. Se informan al cliente el límite y las fichas restantes.
        response.setHeader("X-RateLimit-Limit", String.valueOf(limite.capacidad()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(sonda.getRemainingTokens()));

        // 3. Con ficha disponible, la petición sigue su camino.
        if (sonda.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 4. Sin fichas: se calcula cuánto falta para la recarga y se responde 429 con el header Retry-After.
        long segundos = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(sonda.getNanosToWaitForRefill()));
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(segundos));
        escritor.escribir(response, HttpStatus.TOO_MANY_REQUESTS, CodigoError.DEMASIADAS_PETICIONES,
                "Demasiadas peticiones",
                "Ha superado el número de intentos permitidos. Inténtelo de nuevo en " + segundos
                        + (segundos == 1 ? " segundo." : " segundos."),
                request.getRequestURI());
    }

    /** Solo el POST del login y del registro tienen límite; null para cualquier otra petición. */
    private Limite limiteDe(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        }
        return switch (request.getRequestURI()) {
            case RUTA_LOGIN -> login;
            case RUTA_REGISTRO -> registro;
            default -> null;
        };
    }

    /** Cubo que empieza lleno y se recarga por completo cada periodo. */
    private static Bucket nuevoCubo(Limite limite) {
        return Bucket.builder()
                .addLimit(l -> l.capacity(limite.capacidad()).refillGreedy(limite.capacidad(), limite.periodo()))
                .build();
    }
}

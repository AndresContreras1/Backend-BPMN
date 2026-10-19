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

    static final String RUTA_LOGIN = "/api/v1/auth/login";
    static final String RUTA_REGISTRO = "/api/v1/empresas";

    private final Map<String, Bucket> cubos = new ConcurrentHashMap<>();
    private final EscritorDeProblemDetail escritor;
    private final Limite login;
    private final Limite registro;

    /** Capacidad del cubo y cada cuánto se recarga completo. */
    public record Limite(long capacidad, Duration periodo) {
    }

    public RateLimitFilter(EscritorDeProblemDetail escritor, Limite login, Limite registro) {
        this.escritor = escritor;
        this.login = login;
        this.registro = registro;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return limiteDe(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Limite limite = limiteDe(request);
        Bucket cubo = cubos.computeIfAbsent(request.getRequestURI() + "|" + request.getRemoteAddr(), clave -> nuevoCubo(limite));
        ConsumptionProbe sonda = cubo.tryConsumeAndReturnRemaining(1);

        response.setHeader("X-RateLimit-Limit", String.valueOf(limite.capacidad()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(sonda.getRemainingTokens()));

        if (sonda.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long segundos = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(sonda.getNanosToWaitForRefill()));
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(segundos));
        escritor.escribir(response, HttpStatus.TOO_MANY_REQUESTS,
                "Demasiadas peticiones",
                "Ha superado el número de intentos permitidos. Inténtelo de nuevo en " + segundos + " segundos.",
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

    private static Bucket nuevoCubo(Limite limite) {
        return Bucket.builder()
                .addLimit(l -> l.capacity(limite.capacidad()).refillGreedy(limite.capacidad(), limite.periodo()))
                .build();
    }
}

package co.edu.javeriana.bpmn.security;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * API stateless con JWT en el header Authorization. CSRF se desactiva porque el token no viaja
 * en una cookie: el navegador no lo agrega solo a una petición de otro sitio.
 * Los permisos por rol se declaran en los servicios con @PreAuthorize. El login y el registro tienen
 * límite de intentos por IP (RateLimitFilter).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final CustomUserDetailService userDetailService;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final ApiAccessDeniedHandler apiAccessDeniedHandler;
    private final EscritorDeProblemDetail escritor;
    private final List<String> origenesPermitidos;
    private final RateLimitFilter.Limite limiteLogin;
    private final RateLimitFilter.Limite limiteRegistro;

    public SecurityConfig(JwtService jwtService, CustomUserDetailService userDetailService,
                          JwtAuthEntryPoint jwtAuthEntryPoint, ApiAccessDeniedHandler apiAccessDeniedHandler,
                          EscritorDeProblemDetail escritor,
                          @Value("${app.cors.allowed-origins}") String origenesPermitidos,
                          @Value("${app.rate-limit.login.capacidad}") long capacidadLogin,
                          @Value("${app.rate-limit.login.minutos}") long minutosLogin,
                          @Value("${app.rate-limit.registro.capacidad}") long capacidadRegistro,
                          @Value("${app.rate-limit.registro.minutos}") long minutosRegistro) {
        this.jwtService = jwtService;
        this.userDetailService = userDetailService;
        this.jwtAuthEntryPoint = jwtAuthEntryPoint;
        this.apiAccessDeniedHandler = apiAccessDeniedHandler;
        this.escritor = escritor;
        this.limiteLogin = new RateLimitFilter.Limite(capacidadLogin, Duration.ofMinutes(minutosLogin));
        this.limiteRegistro = new RateLimitFilter.Limite(capacidadRegistro, Duration.ofMinutes(minutosRegistro));
        this.origenesPermitidos = Arrays.stream(origenesPermitidos.split(","))
                .map(String::trim).filter(o -> !o.isEmpty()).toList();
    }

    /** Consola de H2 solo en desarrollo; usa frames, por eso se permite sameOrigin. */
    @Bean
    @Order(1)
    @Profile("default")
    SecurityFilterChain consolaH2(HttpSecurity http) throws Exception {
        http.securityMatcher(PathRequest.toH2Console())
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests
                .requestMatchers(HttpMethod.POST, "/api/v1/empresas", "/api/v1/auth/login").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthEntryPoint)
                .accessDeniedHandler(apiAccessDeniedHandler))
            // Primero el límite de intentos (antes de autenticar) y después el JWT
            .addFilterBefore(new RateLimitFilter(escritor, limiteLogin, limiteRegistro),
                    UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, userDetailService),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenesPermitidos);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT));
        config.setExposedHeaders(List.of(HttpHeaders.LOCATION));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}

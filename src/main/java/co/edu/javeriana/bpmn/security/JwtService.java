package co.edu.javeriana.bpmn.security;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import co.edu.javeriana.bpmn.gestion.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Emite y valida los JWT de la API. El token lleva el correo (sub), el id del usuario (uid),
 * la empresa (eid) y el rol; va firmado, no cifrado, así que no lleva datos sensibles.
 */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int BYTES_MINIMOS_LLAVE = 32;

    private final SecretKey key;
    private final long expiracionSegundos;

    public JwtService(@Value("${app.jwt.secret:}") String secreto,
                      @Value("${app.jwt.expiration-seconds:1800}") long expiracionSegundos) {
        this.key = crearLlave(secreto);
        this.expiracionSegundos = expiracionSegundos;
    }

    static SecretKey crearLlave(String secreto) {
        if (secreto == null || secreto.isBlank()) {
            log.warn("JWT_SECRET no está definido: se genera una llave temporal y los tokens no sobreviven a un reinicio");
            return Jwts.SIG.HS512.key().build();
        }
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secreto.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException("JWT_SECRET debe estar codificado en Base64", e);
        }
        if (bytes.length < BYTES_MINIMOS_LLAVE) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 256 bits (32 bytes)");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generarToken(Usuario usuario) {
        return generarToken(usuario, Instant.now());
    }

    public String generarToken(Usuario usuario, Instant emision) {
        return Jwts.builder()
                .subject(usuario.getCorreo())
                .claim("uid", usuario.getId())
                .claim("eid", usuario.getEmpresaId())
                .claim("rol", usuario.getRol().name())
                .issuedAt(Date.from(emision))
                .expiration(Date.from(emision.plusSeconds(expiracionSegundos)))
                .signWith(key)
                .compact();
    }

    /** Id del usuario si la firma y la fecha de expiración son válidas; vacío en cualquier otro caso. */
    public Optional<Long> usuarioIdSiEsValido(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.ofNullable(claims.get("uid", Long.class));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getExpiracionSegundos() {
        return expiracionSegundos;
    }
}

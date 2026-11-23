package cl.duoc.veterinaria.security;

import cl.duoc.veterinaria.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Crea y valida los JWT firmados con HMAC-SHA256.
 *  - accessToken: dura 15 minutos y se envía en cada petición (Authorization: Bearer ...).
 *  - refreshToken: dura 7 días y solo sirve para pedir un par nuevo en /api/auth/refresh.
 */
@Service
public class JwtService {

    public static final String ACCESS = "access";
    public static final String REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-minutes}") long accessMinutes,
                      @Value("${app.jwt.refresh-days}") long refreshDays) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) debe tener al menos 32 caracteres.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtl = Duration.ofMinutes(accessMinutes);
        this.refreshTtl = Duration.ofDays(refreshDays);
    }

    public String createAccessToken(User user, String sessionId) {
        return build(user, sessionId, ACCESS, accessTtl);
    }

    public String createRefreshToken(User user, String sessionId) {
        return build(user, sessionId, REFRESH, refreshTtl);
    }

    public Instant refreshExpiresAt() {
        return Instant.now().plus(refreshTtl);
    }

    public record TokenIdentity(Long userId, String sessionId) { }

    private String build(User user, String sessionId, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId)
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * Valida firma, expiración y tipo. Devuelve el id del usuario si el token es válido.
     * El rol NO se toma del token: se vuelve a leer desde la base de datos.
     */
    public Optional<TokenIdentity> validate(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!expectedType.equals(claims.get("type", String.class))) {
                return Optional.empty();
            }
            String sessionId = claims.get("sid", String.class);
            if (sessionId == null || sessionId.isBlank()) return Optional.empty();
            return Optional.of(new TokenIdentity(Long.valueOf(claims.getSubject()), sessionId));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

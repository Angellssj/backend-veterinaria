package cl.duoc.veterinaria.service;

import cl.duoc.veterinaria.dto.AuthDtos.AuthResponse;
import cl.duoc.veterinaria.dto.AuthDtos.LoginRequest;
import cl.duoc.veterinaria.dto.AuthDtos.RegisterRequest;
import cl.duoc.veterinaria.dto.UserDto;
import cl.duoc.veterinaria.exception.ApiException;
import cl.duoc.veterinaria.model.Role;
import cl.duoc.veterinaria.model.User;
import cl.duoc.veterinaria.repository.UserRepository;
import cl.duoc.veterinaria.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import cl.duoc.veterinaria.model.LoginSession;
import cl.duoc.veterinaria.repository.LoginSessionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Correo o contraseña incorrectos.";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginSessionRepository sessions;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, LoginSessionRepository sessions) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.sessions = sessions;
    }

    /** Todo usuario que se registra desde la web queda como CLIENTE. */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (req.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("La contraseña no puede superar 72 bytes UTF-8.");
        }
        String email = req.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Este correo ya está registrado.");
        }
        User user = users.save(new User(req.name().trim(), email, encoder.encode(req.password()), Role.CLIENTE));
        return tokensFor(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        // BCrypt solo considera 72 bytes; una contraseña más larga nunca es válida aquí
        if (req.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized(BAD_CREDENTIALS));
        return tokensFor(user);
    }

    /** La renovación rota el secreto y bloquea la fila para que no pueda reutilizarse. */
    @Transactional
    public AuthResponse refresh(String refreshToken) {
        var identity = jwt.validate(refreshToken, JwtService.REFRESH)
                .orElseThrow(() -> ApiException.unauthorized("Sesión expirada. Inicia sesión nuevamente."));
        LoginSession session = sessions.findForUpdate(identity.sessionId())
                .filter(s -> !s.isRevoked() && s.getExpiresAt().isAfter(Instant.now()))
                .filter(s -> s.getUser().getId().equals(identity.userId()))
                .filter(s -> MessageDigest.isEqual(s.getRefreshHash().getBytes(StandardCharsets.UTF_8),
                        hash(refreshToken).getBytes(StandardCharsets.UTF_8)))
                .orElseThrow(() -> ApiException.unauthorized("Token de renovación inválido o utilizado."));
        return rotate(session);
    }

    @Transactional
    public void logout(String sessionId) {
        sessions.findForUpdate(sessionId).ifPresent(s -> s.setRevoked(true));
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return users.findById(userId)
                .map(UserDto::from)
                .orElseThrow(() -> ApiException.unauthorized("Usuario no encontrado."));
    }

    private AuthResponse tokensFor(User user) {
        LoginSession session = new LoginSession();
        session.setId(UUID.randomUUID().toString());
        session.setUser(user);
        AuthResponse response = rotate(session);
        sessions.save(session);
        return response;
    }

    private AuthResponse rotate(LoginSession session) {
        String refresh = jwt.createRefreshToken(session.getUser(), session.getId());
        session.setRefreshHash(hash(refresh));
        session.setExpiresAt(jwt.refreshExpiresAt());
        return new AuthResponse(jwt.createAccessToken(session.getUser(), session.getId()), refresh,
                UserDto.from(session.getUser()));
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

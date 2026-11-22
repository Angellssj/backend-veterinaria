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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Correo o contraseña incorrectos.";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    /** Todo usuario que se registra desde la web queda como CLIENTE. */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Este correo ya está registrado.");
        }
        User user = users.save(new User(req.name().trim(), email, encoder.encode(req.password()), Role.CLIENTE));
        return tokensFor(user);
    }

    @Transactional(readOnly = true)
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

    /** Entrega un par nuevo de tokens si el refreshToken es válido y el usuario sigue existiendo. */
    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        User user = jwt.validate(refreshToken, JwtService.REFRESH)
                .flatMap(users::findById)
                .orElseThrow(() -> ApiException.unauthorized("Sesión expirada. Inicia sesión nuevamente."));
        return tokensFor(user);
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return users.findById(userId)
                .map(UserDto::from)
                .orElseThrow(() -> ApiException.unauthorized("Usuario no encontrado."));
    }

    private AuthResponse tokensFor(User user) {
        return new AuthResponse(jwt.createAccessToken(user), jwt.createRefreshToken(user), UserDto.from(user));
    }
}

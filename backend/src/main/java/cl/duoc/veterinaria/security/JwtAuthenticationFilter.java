package cl.duoc.veterinaria.security;

import cl.duoc.veterinaria.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lee "Authorization: Bearer <token>", valida el accessToken y carga el usuario desde la base.
 * Si el token falta o no es válido, la petición sigue sin autenticación y Spring Security
 * responde 401 en las rutas protegidas (el frontend entonces intenta renovar el token).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final cl.duoc.veterinaria.repository.LoginSessionRepository sessions;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository, cl.duoc.veterinaria.repository.LoginSessionRepository sessions) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7).trim();
            jwtService.validate(token, JwtService.ACCESS)
                    .filter(identity -> sessions.existsByIdAndUser_IdAndRevokedFalseAndExpiresAtAfter(
                            identity.sessionId(), identity.userId(), java.time.Instant.now()))
                    .ifPresent(identity -> userRepository.findById(identity.userId()).ifPresent(user -> {
                        AuthUser principal = new AuthUser(user.getId(), user.getEmail(), user.getRole(), identity.sessionId());
                        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }));
        }
        chain.doFilter(request, response);
    }
}

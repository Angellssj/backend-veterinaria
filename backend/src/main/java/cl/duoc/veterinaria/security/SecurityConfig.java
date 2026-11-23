package cl.duoc.veterinaria.security;

import cl.duoc.veterinaria.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
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
 * Reglas de acceso (la misma tabla de permisos que usa el frontend):
 *
 *  Público   : GET productos y categorías, login, registro, refresh, health, Swagger.
 *  Con sesión: /api/auth/me, crear pedido, /api/orders/mine.
 *  Solo ADMIN: crear/editar/eliminar productos, ver todos los pedidos, cambiar estado, usuarios y roles.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   UserRepository userRepository, cl.duoc.veterinaria.repository.LoginSessionRepository sessions) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // API sin cookies: el token va en la cabecera
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.frameOptions(f -> f.sameOrigin())) // permite la consola H2 en desarrollo
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/", "/error", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/h2-console/**").permitAll();
                    for (String base : List.of("/api/v1", "/api")) {
                        auth.requestMatchers(base + "/health").permitAll()
                            .requestMatchers(HttpMethod.POST, base + "/auth/login", base + "/auth/register", base + "/auth/refresh").permitAll()
                            .requestMatchers(HttpMethod.GET, base + "/products", base + "/products/*", base + "/categories").permitAll()
                            .requestMatchers(HttpMethod.POST, base + "/products").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.PUT, base + "/products/*").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.DELETE, base + "/products/*").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.GET, base + "/orders/mine").hasAnyRole("CLIENTE", "ADMIN")
                            .requestMatchers(HttpMethod.POST, base + "/orders").hasAnyRole("CLIENTE", "ADMIN")
                            .requestMatchers(HttpMethod.GET, base + "/orders", base + "/orders/*").hasAnyRole("ADMIN", "VENDEDOR")
                            .requestMatchers(HttpMethod.PATCH, base + "/orders/*/status").hasRole("ADMIN")
                            .requestMatchers(base + "/users", base + "/users/**").hasRole("ADMIN");
                    }
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(res, HttpServletResponse.SC_UNAUTHORIZED, "Debes iniciar sesión."))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(res, HttpServletResponse.SC_FORBIDDEN, "No tienes permisos para esta acción.")))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository, sessions),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** CORS: solo los orígenes configurados (localhost en desarrollo, la URL de S3 en AWS). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> list = Arrays.stream(origins.split(","))
                .map(String::trim)
                .map(o -> o.endsWith("/") ? o.substring(0, o.length() - 1) : o)
                .filter(o -> !o.isEmpty())
                .toList();
        config.setAllowedOriginPatterns(list);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static void writeError(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}

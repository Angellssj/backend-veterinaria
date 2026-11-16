package cl.duoc.veterinaria.controller;

import cl.duoc.veterinaria.dto.AuthDtos.AuthResponse;
import cl.duoc.veterinaria.dto.AuthDtos.LoginRequest;
import cl.duoc.veterinaria.dto.AuthDtos.RefreshRequest;
import cl.duoc.veterinaria.dto.AuthDtos.RegisterRequest;
import cl.duoc.veterinaria.dto.UserDto;
import cl.duoc.veterinaria.security.AuthUser;
import cl.duoc.veterinaria.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un cliente nuevo y devolver sus tokens")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión con correo y contraseña")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar tokens con el refreshToken")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return auth.refresh(req.refreshToken());
    }

    @GetMapping("/me")
    @Operation(summary = "Usuario de la sesión actual (rol leído desde la base de datos)")
    public UserDto me(@AuthenticationPrincipal AuthUser user) {
        return auth.me(user.id());
    }
}

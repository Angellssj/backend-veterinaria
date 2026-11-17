package cl.duoc.veterinaria.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Peticiones y respuestas de /api/auth. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "El correo es obligatorio.") @Email(message = "Correo inválido.") String email,
            @NotBlank(message = "La contraseña es obligatoria.") String password) {
    }

    public record RegisterRequest(
            @NotBlank(message = "El nombre es obligatorio.") @Size(max = 120, message = "Máximo 120 caracteres.") String name,
            @NotBlank(message = "El correo es obligatorio.") @Email(message = "Correo inválido.") @Size(max = 160, message = "Máximo 160 caracteres.") String email,
            @NotBlank(message = "La contraseña es obligatoria.") @Size(min = 6, max = 72, message = "La contraseña debe tener entre 6 y 72 caracteres.") String password) {
    }

    public record RefreshRequest(@NotBlank(message = "Falta el refreshToken.") String refreshToken) {
    }

    /** Respuesta de login, registro y refresh. El frontend la guarda tal cual en localStorage. */
    public record AuthResponse(String accessToken, String refreshToken, String tokenType, UserDto user) {

        public AuthResponse(String accessToken, String refreshToken, UserDto user) {
            this(accessToken, refreshToken, "Bearer", user);
        }
    }
}

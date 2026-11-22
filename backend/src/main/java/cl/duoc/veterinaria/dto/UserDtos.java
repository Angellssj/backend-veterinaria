package cl.duoc.veterinaria.dto;

import jakarta.validation.constraints.NotBlank;

public final class UserDtos {

    private UserDtos() {
    }

    public record RoleRequest(@NotBlank(message = "El rol es obligatorio.") String role) {
    }
}

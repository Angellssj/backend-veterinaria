package cl.duoc.veterinaria.dto;

import cl.duoc.veterinaria.model.User;

/** Datos públicos de un usuario (nunca incluye el hash de la contraseña). */
public record UserDto(Long id, String name, String email, String role) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getRole().name());
    }
}

package cl.duoc.veterinaria.security;

import cl.duoc.veterinaria.model.Role;

/** Usuario autenticado de la petición actual (se obtiene con @AuthenticationPrincipal). */
public record AuthUser(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}

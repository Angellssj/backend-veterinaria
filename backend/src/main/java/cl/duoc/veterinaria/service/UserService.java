package cl.duoc.veterinaria.service;

import cl.duoc.veterinaria.dto.UserDto;
import cl.duoc.veterinaria.exception.ApiException;
import cl.duoc.veterinaria.model.Role;
import cl.duoc.veterinaria.model.User;
import cl.duoc.veterinaria.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return users.findAllByOrderByIdAsc().stream().map(UserDto::from).toList();
    }

    /** Un administrador no puede cambiarse su propio rol (evita quedarse sin administradores). */
    @Transactional
    public UserDto changeRole(Long currentUserId, Long id, String rawRole) {
        if (id.equals(currentUserId)) {
            throw ApiException.badRequest("No puedes cambiar tu propio rol.");
        }
        Role role;
        try {
            role = Role.valueOf(rawRole.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Rol inválido. Use: CLIENTE o ADMIN.");
        }
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("Usuario no encontrado."));
        user.setRole(role);
        return UserDto.from(user);
    }
}

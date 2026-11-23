package cl.duoc.veterinaria.controller;

import cl.duoc.veterinaria.dto.UserDto;
import cl.duoc.veterinaria.dto.UserDtos.RoleRequest;
import cl.duoc.veterinaria.security.AuthUser;
import cl.duoc.veterinaria.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@RequestMapping({"/api/v1/users", "/api/users"})
@Tag(name = "Usuarios (ADMIN)")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar usuarios (ADMIN)")
    public List<UserDto> list() {
        return service.list();
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Cambiar el rol de un usuario (ADMIN)")
    public UserDto changeRole(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id,
                              @Valid @RequestBody RoleRequest req) {
        return service.changeRole(admin.id(), id, req.role());
    }
}

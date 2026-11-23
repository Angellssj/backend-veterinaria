package cl.duoc.veterinaria.controller;

import cl.duoc.veterinaria.dto.OrderDtos.CreateOrderRequest;
import cl.duoc.veterinaria.dto.OrderDtos.OrderDto;
import cl.duoc.veterinaria.dto.OrderDtos.StatusRequest;
import cl.duoc.veterinaria.security.AuthUser;
import cl.duoc.veterinaria.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@RequestMapping({"/api/v1/orders", "/api/orders"})
@Tag(name = "Pedidos")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Confirmar compra (con sesión). El total lo calcula el servidor")
    public OrderDto create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateOrderRequest req) {
        return service.create(user.id(), req);
    }

    @GetMapping("/mine")
    @Operation(summary = "Pedidos del usuario de la sesión")
    public List<OrderDto> mine(@AuthenticationPrincipal AuthUser user) {
        return service.mine(user.id());
    }

    @GetMapping
    @Operation(summary = "Todos los pedidos (ADMIN o VENDEDOR)")
    public List<OrderDto> all() {
        return service.all();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un pedido (ADMIN o VENDEDOR)")
    public OrderDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Cambiar estado del pedido (ADMIN). Al cancelar se repone el stock")
    public OrderDto updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return service.updateStatus(id, req.status());
    }
}

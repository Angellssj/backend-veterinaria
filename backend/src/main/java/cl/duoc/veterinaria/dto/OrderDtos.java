package cl.duoc.veterinaria.dto;

import cl.duoc.veterinaria.model.Order;
import cl.duoc.veterinaria.model.OrderItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    /**
     * Pedido enviado desde el checkout. Solo trae id y cantidad de cada producto:
     * los precios y el total los calcula el servidor.
     */
    public record CreateOrderRequest(
            @NotBlank(message = "El nombre es obligatorio.") @Size(max = 120, message = "Máximo 120 caracteres.") String name,
            @NotBlank(message = "El correo es obligatorio.") @Email(message = "Correo inválido.") @Size(max = 160, message = "Máximo 160 caracteres.") String email,
            @NotBlank(message = "La dirección es obligatoria.") @Size(max = 255, message = "Máximo 255 caracteres.") String address,
            @Size(max = 60, message = "Máximo 60 caracteres.") String delivery,
            @Size(max = 60, message = "Máximo 60 caracteres.") String payment,
            @NotEmpty(message = "El carrito está vacío.") @Size(max = 50, message = "Demasiados productos en un pedido.") List<@NotNull(message = "El producto no puede ser nulo.") @Valid ItemRequest> items) {
    }

    public record ItemRequest(
            @NotNull(message = "Falta el producto.") Long productId,
            @NotNull(message = "Falta la cantidad.") @Min(value = 1, message = "La cantidad mínima es 1.") @Max(value = 999, message = "Cantidad demasiado alta.") Integer quantity) {
    }

    public record StatusRequest(@NotBlank(message = "El estado es obligatorio.") String status) {
    }

    public record CustomerDto(String name, String email, String address, String delivery, String payment) {
    }

    public record OrderItemDto(Long productId, String name, Integer price, Integer quantity, Long subtotal) {

        public static OrderItemDto from(OrderItem item) {
            return new OrderItemDto(item.getProduct().getId(), item.getProductName(), item.getUnitPrice(),
                    item.getQuantity(), item.getSubtotal());
        }
    }

    /** Formato que leen MyOrders.jsx, Admin.jsx y Result.jsx. */
    public record OrderDto(Long id, Long userId, CustomerDto customer, List<OrderItemDto> items,
                           Long total, String status, String createdAt) {

        public static OrderDto from(Order o) {
            return new OrderDto(
                    o.getId(),
                    o.getUser().getId(),
                    new CustomerDto(o.getCustomerName(), o.getCustomerEmail(), o.getAddress(), o.getDelivery(), o.getPayment()),
                    o.getItems().stream().map(OrderItemDto::from).toList(),
                    o.getTotal(),
                    o.getStatus().name(),
                    o.getCreatedAt().toString());
        }
    }
}

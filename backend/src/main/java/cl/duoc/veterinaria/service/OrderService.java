package cl.duoc.veterinaria.service;

import cl.duoc.veterinaria.dto.OrderDtos.CreateOrderRequest;
import cl.duoc.veterinaria.dto.OrderDtos.ItemRequest;
import cl.duoc.veterinaria.dto.OrderDtos.OrderDto;
import cl.duoc.veterinaria.exception.ApiException;
import cl.duoc.veterinaria.model.Order;
import cl.duoc.veterinaria.model.OrderItem;
import cl.duoc.veterinaria.model.OrderStatus;
import cl.duoc.veterinaria.model.Product;
import cl.duoc.veterinaria.model.User;
import cl.duoc.veterinaria.repository.OrderRepository;
import cl.duoc.veterinaria.repository.ProductRepository;
import cl.duoc.veterinaria.repository.UserRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final ProductRepository products;
    private final UserRepository users;

    public OrderService(OrderRepository orders, ProductRepository products, UserRepository users) {
        this.orders = orders;
        this.products = products;
        this.users = users;
    }

    /**
     * Crea el pedido en una sola transacción:
     *  1. Junta cantidades si el mismo producto viene repetido.
     *  2. Verifica que todos los productos existan y tengan stock (si uno falla, no se descuenta nada).
     *  3. Calcula precios y total con los valores de la base (no se confía en el navegador).
     *  4. Descuenta stock y guarda el pedido como CONFIRMADO.
     */
    @Transactional
    public OrderDto create(Long userId, CreateOrderRequest req) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Usuario no encontrado."));

        Map<Long, Integer> quantities = new java.util.TreeMap<>();
        for (ItemRequest item : req.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }

        Map<Long, Product> found = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = products.findActiveForUpdate(entry.getKey())
                    .orElseThrow(() -> ApiException.badRequest("Producto inexistente: " + entry.getKey() + "."));
            if (entry.getValue() > product.getStock()) {
                throw ApiException.conflict("Stock insuficiente para \"" + product.getName()
                        + "\" (disponible: " + product.getStock() + ").");
            }
            found.put(product.getId(), product);
        }

        Order order = new Order();
        order.setUser(user);
        order.setCustomerName(req.name().trim());
        order.setCustomerEmail(req.email().trim());
        order.setAddress(req.address().trim());
        order.setDelivery(orDefault(req.delivery(), "Despacho a domicilio"));
        order.setPayment(orDefault(req.payment(), "Tarjeta de débito"));
        order.setStatus(OrderStatus.CONFIRMADO);
        order.setCreatedAt(Instant.now());

        long total = 0;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = found.get(entry.getKey());
            int qty = entry.getValue();
            product.setStock(product.getStock() - qty);

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setUnitPrice(product.getPrice());
            item.setQuantity(qty);
            item.setSubtotal((long) product.getPrice() * qty);
            order.addItem(item);
            total += item.getSubtotal();
        }
        order.setTotal(total);

        return OrderDto.from(orders.save(order));
    }

    @Transactional(readOnly = true)
    public List<OrderDto> mine(Long userId) {
        return orders.findAllByUser_IdOrderByCreatedAtDesc(userId).stream().map(OrderDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDto> all() {
        return orders.findAllByOrderByCreatedAtDesc().stream().map(OrderDto::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderDto get(Long id) {
        return orders.findById(id).map(OrderDto::from)
                .orElseThrow(() -> ApiException.notFound("Pedido no encontrado."));
    }

    /**
     * Cambia el estado (solo ADMIN). Un pedido CANCELADO ya no cambia.
     * Al cancelar, el stock de cada producto se devuelve al inventario.
     */
    @Transactional
    public OrderDto updateStatus(Long id, String rawStatus) {
        OrderStatus status = parseStatus(rawStatus);
        Order order = orders.findForUpdate(id).orElseThrow(() -> ApiException.notFound("Pedido no encontrado."));
        if (order.getStatus() == OrderStatus.CANCELADO) {
            throw ApiException.conflict("El pedido #" + id + " está cancelado y no se puede modificar.");
        }
        if (order.getStatus() == OrderStatus.ENTREGADO && status != OrderStatus.ENTREGADO) {
            throw ApiException.conflict("Un pedido entregado no se puede modificar.");
        }
        if (status != OrderStatus.CANCELADO && status.ordinal() < order.getStatus().ordinal()) {
            throw ApiException.conflict("El pedido no puede volver a un estado anterior.");
        }
        if (status == OrderStatus.CANCELADO) {
            for (OrderItem item : order.getItems()) {
                Product product = products.findForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> ApiException.notFound("Producto no encontrado."));
                product.setStock(product.getStock() + item.getQuantity());
            }
        }
        order.setStatus(status);
        return OrderDto.from(order);
    }

    private static OrderStatus parseStatus(String raw) {
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            String valid = Arrays.stream(OrderStatus.values()).map(Enum::name).collect(Collectors.joining(", "));
            throw ApiException.badRequest("Estado inválido. Use: " + valid + ".");
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

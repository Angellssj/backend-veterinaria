package cl.duoc.veterinaria.model;

/** Estados de un pedido. Los mismos valores que muestra el frontend (MyOrders.jsx). */
public enum OrderStatus {
    CONFIRMADO,
    EN_PREPARACION,
    DESPACHADO,
    ENTREGADO,
    CANCELADO
}

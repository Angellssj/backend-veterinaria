package cl.duoc.veterinaria.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** Precio en pesos chilenos (CLP, sin decimales). */
    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private boolean offer;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, length = 16)
    private String emoji;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    /**
     * Eliminación lógica: un producto "eliminado" queda inactivo para no romper
     * el historial de pedidos que lo referencian.
     */
    @Column(nullable = false)
    private boolean active = true;
}

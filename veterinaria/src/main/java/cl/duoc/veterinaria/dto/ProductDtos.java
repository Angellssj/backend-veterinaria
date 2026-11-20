package cl.duoc.veterinaria.dto;

import cl.duoc.veterinaria.model.Category;
import cl.duoc.veterinaria.model.Product;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProductDtos {

    private ProductDtos() {
    }

    /** Lo que envía el panel de administración al crear o editar un producto. */
    public record ProductRequest(
            @NotBlank(message = "El nombre es obligatorio.") @Size(max = 120, message = "Máximo 120 caracteres.") String name,
            @NotBlank(message = "La categoría es obligatoria.") String category,
            @NotNull(message = "El precio es obligatorio.") @Min(value = 0, message = "El precio debe ser mayor o igual a 0.") @Max(value = 100_000_000, message = "Precio demasiado alto.") Integer price,
            @NotNull(message = "El stock es obligatorio.") @Min(value = 0, message = "El stock debe ser mayor o igual a 0.") @Max(value = 1_000_000, message = "Stock demasiado alto.") Integer stock,
            Boolean offer,
            @NotBlank(message = "La descripción es obligatoria.") @Size(max = 500, message = "Máximo 500 caracteres.") String description,
            @Size(max = 16, message = "Emoji demasiado largo.") String emoji,
            // Solo rutas del propio sitio (/img/...) o URLs https: evita esquemas como javascript: o data:.
            @Size(max = 255, message = "La URL de la imagen es demasiado larga.")
            @Pattern(regexp = "^(/img/[\\w./-]+|https://[^\\s\"'<>]+)?$",
                    message = "La imagen debe ser una ruta /img/... o una URL https://") String imageUrl) {
    }

    /** Producto tal como lo consume el frontend: la categoría va como texto. */
    public record ProductDto(Long id, String name, String category, Integer price, Integer stock,
                             boolean offer, String description, String emoji, String imageUrl) {

        public static ProductDto from(Product p) {
            return new ProductDto(p.getId(), p.getName(), p.getCategory().getName(), p.getPrice(), p.getStock(),
                    p.isOffer(), p.getDescription(), p.getEmoji(), p.getImageUrl());
        }
    }

    public record CategoryDto(Long id, String name) {

        public static CategoryDto from(Category c) {
            return new CategoryDto(c.getId(), c.getName());
        }
    }
}

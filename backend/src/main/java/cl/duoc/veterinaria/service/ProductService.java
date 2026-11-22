package cl.duoc.veterinaria.service;

import cl.duoc.veterinaria.dto.ProductDtos.CategoryDto;
import cl.duoc.veterinaria.dto.ProductDtos.ProductDto;
import cl.duoc.veterinaria.dto.ProductDtos.ProductRequest;
import cl.duoc.veterinaria.exception.ApiException;
import cl.duoc.veterinaria.model.Category;
import cl.duoc.veterinaria.model.Product;
import cl.duoc.veterinaria.repository.CategoryRepository;
import cl.duoc.veterinaria.repository.ProductRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;

    public ProductService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    /** Catálogo con filtros opcionales: categoría, solo ofertas y texto de búsqueda. */
    @Transactional(readOnly = true)
    public List<ProductDto> list(String category, Boolean offer, String q) {
        String text = q == null ? "" : q.trim().toLowerCase();
        return products.findAllByActiveTrueOrderByIdAsc().stream()
                .filter(p -> category == null || category.isBlank() || p.getCategory().getName().equalsIgnoreCase(category))
                .filter(p -> offer == null || !offer || p.isOffer())
                .filter(p -> text.isEmpty() || (p.getName() + " " + p.getDescription()).toLowerCase().contains(text))
                .map(ProductDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductDto get(Long id) {
        return ProductDto.from(findActive(id));
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> categories() {
        return categories.findAllByOrderByNameAsc().stream().map(CategoryDto::from).toList();
    }

    @Transactional
    public ProductDto create(ProductRequest req) {
        Product product = new Product();
        apply(product, req);
        return ProductDto.from(products.save(product));
    }

    @Transactional
    public ProductDto update(Long id, ProductRequest req) {
        Product product = findActive(id);
        apply(product, req);
        return ProductDto.from(product);
    }

    /** Eliminación lógica: deja de aparecer en el catálogo, pero se conserva en pedidos antiguos. */
    @Transactional
    public void delete(Long id) {
        findActive(id).setActive(false);
    }

    private Product findActive(Long id) {
        return products.findByIdAndActiveTrue(id).orElseThrow(() -> ApiException.notFound("Producto no encontrado."));
    }

    private void apply(Product product, ProductRequest req) {
        Category category = categories.findByNameIgnoreCase(req.category().trim())
                .orElseThrow(() -> ApiException.badRequest("Categoría inválida. Use: " + categoryNames() + "."));
        product.setName(req.name().trim());
        product.setCategory(category);
        product.setPrice(req.price());
        product.setStock(req.stock());
        product.setOffer(Boolean.TRUE.equals(req.offer()));
        product.setDescription(req.description().trim());
        String emoji = req.emoji() == null || req.emoji().isBlank() ? "🐾" : req.emoji().trim();
        product.setEmoji(emoji);
    }

    private String categoryNames() {
        return categories.findAllByOrderByNameAsc().stream().map(Category::getName).collect(Collectors.joining(", "));
    }
}

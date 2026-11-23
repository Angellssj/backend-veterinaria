package cl.duoc.veterinaria.controller;

import cl.duoc.veterinaria.dto.ProductDtos.CategoryDto;
import cl.duoc.veterinaria.dto.ProductDtos.ProductDto;
import cl.duoc.veterinaria.dto.ProductDtos.ProductRequest;
import cl.duoc.veterinaria.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1", "/api"})
@Tag(name = "Productos")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/products")
    @Operation(summary = "Catálogo (público). Filtros opcionales: ?category=Salud&offer=true&q=perro")
    public List<ProductDto> list(@RequestParam(required = false) String category,
                                 @RequestParam(required = false) Boolean offer,
                                 @RequestParam(required = false) String q) {
        return service.list(category, offer, q);
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Detalle de un producto (público)")
    public ProductDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/categories")
    @Operation(summary = "Categorías (público)")
    public List<CategoryDto> categories() {
        return service.categories();
    }

    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear producto (ADMIN)")
    public ProductDto create(@Valid @RequestBody ProductRequest req) {
        return service.create(req);
    }

    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PutMapping("/products/{id}")
    @Operation(summary = "Editar producto (ADMIN)")
    public ProductDto update(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        return service.update(id, req);
    }

    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar producto (ADMIN, eliminación lógica)")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}

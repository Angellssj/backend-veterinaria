package cl.duoc.veterinaria.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Estado del servicio. AWS (Elastic Beanstalk / balanceador) lo usa como health check. */
@RestController
@Tag(name = "Salud")
public class HealthController {

    @GetMapping({"/", "/api/v1/health", "/api/health"})
    public Map<String, Object> health() {
        return Map.of("ok", true, "service", "veterinaria-san-marcos");
    }
}

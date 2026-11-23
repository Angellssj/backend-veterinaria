package cl.duoc.veterinaria.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI en /swagger-ui.html con botón "Authorize" para pegar el accessToken
 * (se obtiene con POST /api/auth/login).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI veterinariaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Veterinaria San Marcos")
                        .version("1.0.0")
                        .description("API REST de la tienda Veterinaria San Marcos (DSY1104 · Duoc UC · Equipo ROANG)."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}

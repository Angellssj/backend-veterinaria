package cl.duoc.veterinaria;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RubricaIntegrationTests {
    @Value("${local.server.port}") int port;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private HttpRequest request(String method, String path, String body, String token) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(30));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
    }
    private HttpResponse<String> call(String method, String path, String body, String token) throws Exception {
        return http.send(request(method, path, body, token), HttpResponse.BodyHandlers.ofString());
    }
    private String text(String json, String name) {
        var match = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertTrue(match.find(), json); return match.group(1);
    }
    private long number(String json, String name) {
        var match = Pattern.compile("\"" + name + "\"\\s*:\\s*(\\d+)").matcher(json);
        assertTrue(match.find(), json); return Long.parseLong(match.group(1));
    }
    private String loginBody(String email, String password) throws Exception {
        var result = call("POST", "/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
        assertEquals(200, result.statusCode(), result.body()); return result.body();
    }
    private String admin() throws Exception { return text(loginBody("admin@sanmarcos.cl", "Admin123!"), "accessToken"); }
    private String client() throws Exception { return text(loginBody("cliente@sanmarcos.cl", "Cliente123!"), "accessToken"); }
    private String productBody(int stock, String image) {
        return """
            {"name":"Producto de prueba EP3","category":"Salud","price":1000,"stock":%d,
             "description":"Prueba de integración","emoji":"🐾","imageUrl":"%s"}
            """.formatted(stock, image);
    }
    private long product(int stock, String admin) throws Exception {
        var res = call("POST", "/api/v1/products", productBody(stock, "/img/productos/gato.webp"), admin);
        assertEquals(201, res.statusCode(), res.body()); return number(res.body(), "id");
    }
    private String order(long id, int quantity) {
        return """
            {"name":"Cliente Demo","email":"cliente@sanmarcos.cl","address":"Calle de prueba 123",
             "items":[{"productId":%d,"quantity":%d}]}
            """.formatted(id, quantity);
    }

    @Test void apiVersionadaYSwaggerDocumentanLaVersionPublicada() throws Exception {
        assertEquals(200, call("GET", "/api/v1/products", null, null).statusCode());
        var docs = call("GET", "/v3/api-docs", null, null);
        assertEquals(200, docs.statusCode(), docs.body());
        assertTrue(docs.body().contains("/api/v1/products"));
        assertTrue(docs.body().contains("/api/v1/auth/logout"));
        assertFalse(docs.body().contains("\"/api/products\""));
        // Alias conservado para clientes anteriores.
        assertEquals(200, call("GET", "/api/products", null, null).statusCode());
    }

    @Test void vendedorSoloConsultaProductosYPedidos() throws Exception {
        String seller = text(loginBody("vendedor@sanmarcos.cl", "Vendedor123!"), "accessToken");
        assertEquals(200, call("GET", "/api/v1/products/1", null, seller).statusCode());
        assertEquals(200, call("GET", "/api/v1/orders", null, seller).statusCode());
        assertEquals(404, call("GET", "/api/v1/orders/999999999", null, seller).statusCode());
        assertEquals(403, call("GET", "/api/v1/users", null, seller).statusCode());
        assertEquals(403, call("GET", "/api/v1/orders/mine", null, seller).statusCode());
        assertEquals(403, call("POST", "/api/v1/orders", order(1, 1), seller).statusCode());
        assertEquals(403, call("POST", "/api/v1/products", productBody(1, ""), seller).statusCode());
        assertEquals(403, call("PUT", "/api/v1/products/1", productBody(1, ""), seller).statusCode());
        assertEquals(403, call("DELETE", "/api/v1/products/1", null, seller).statusCode());
        assertEquals(403, call("PATCH", "/api/v1/orders/1/status", "{\"status\":\"CANCELADO\"}", seller).statusCode());
    }

    @Test void refreshRotaYLogoutRevocaAmbosTokens() throws Exception {
        var login = loginBody("cliente@sanmarcos.cl", "Cliente123!");
        String original = text(login, "refreshToken");
        var renewed = call("POST", "/api/v1/auth/refresh", "{\"refreshToken\":\"" + original + "\"}", null);
        assertEquals(200, renewed.statusCode(), renewed.body());
        assertNotEquals(original, text(renewed.body(), "refreshToken"));
        assertNotEquals(text(login, "accessToken"), text(renewed.body(), "accessToken"));
        assertEquals(401, call("POST", "/api/v1/auth/refresh", "{\"refreshToken\":\"" + original + "\"}", null).statusCode());
        String access = text(renewed.body(), "accessToken");
        assertEquals(204, call("POST", "/api/v1/auth/logout", null, access).statusCode());
        assertEquals(401, call("GET", "/api/v1/auth/me", null, access).statusCode());
        assertEquals(401, call("POST", "/api/v1/auth/refresh", "{\"refreshToken\":\"" + text(renewed.body(), "refreshToken") + "\"}", null).statusCode());
    }

    @Test void fotosPersistenYSeRechazanProtocolosInseguros() throws Exception {
        String admin = admin(); long id = product(3, admin);
        assertTrue(call("GET", "/api/v1/products/" + id, null, null).body().contains("/img/productos/gato.webp"));
        assertEquals(200, call("PUT", "/api/v1/products/" + id, productBody(3, "https://example.org/foto.webp"), admin).statusCode());
        assertTrue(call("GET", "/api/v1/products/" + id, null, null).body().contains("https://example.org/foto.webp"));
        assertEquals(400, call("POST", "/api/v1/products", productBody(3, "javascript:alert(1)"), admin).statusCode());
    }

    @Test void validacionDeEntradasNoTerminaEnError500() throws Exception {
        String body = "{\"name\":\"Prueba\",\"email\":\"unicode@test.cl\",\"password\":\"" + "ñ".repeat(40) + "\"}";
        assertEquals(400, call("POST", "/api/v1/auth/register", body, null).statusCode());
        assertEquals(400, call("POST", "/api/v1/orders", order(1, 1).replace("{\"productId\":1,\"quantity\":1}", "null"), client()).statusCode());
    }

    @Test void stockNoSeSobrevendeConComprasSimultaneas() throws Exception {
        long id = product(1, admin()); String token = client();
        var first = http.sendAsync(request("POST", "/api/v1/orders", order(id, 1), token), HttpResponse.BodyHandlers.ofString());
        var second = http.sendAsync(request("POST", "/api/v1/orders", order(id, 1), token), HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(first, second).join();
        assertEquals(List.of(201, 409), java.util.stream.Stream.of(first.join(), second.join()).map(HttpResponse::statusCode).sorted().toList());
        assertEquals(0, number(call("GET", "/api/v1/products/" + id, null, null).body(), "stock"));
    }

    @Test void dosCancelacionesNoReponenElStockDosVeces() throws Exception {
        String admin = admin(); long id = product(1, admin);
        var created = call("POST", "/api/v1/orders", order(id, 1), client());
        assertEquals(201, created.statusCode(), created.body());
        long orderId = number(created.body(), "id");
        String path = "/api/v1/orders/" + orderId + "/status";
        var first = http.sendAsync(request("PATCH", path, "{\"status\":\"CANCELADO\"}", admin), HttpResponse.BodyHandlers.ofString());
        var second = http.sendAsync(request("PATCH", path, "{\"status\":\"CANCELADO\"}", admin), HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(first, second).join();
        assertEquals(List.of(200, 409), java.util.stream.Stream.of(first.join(), second.join()).map(HttpResponse::statusCode).sorted().toList());
        assertEquals(1, number(call("GET", "/api/v1/products/" + id, null, null).body(), "stock"));
    }

    @Test void cambioDeRolSeAplicaAunqueElTokenAnteriorDigaCliente() throws Exception {
        String email = "rol-" + UUID.randomUUID() + "@test.cl";
        var registered = call("POST", "/api/v1/auth/register", "{\"name\":\"Prueba rol\",\"email\":\"" + email + "\",\"password\":\"Secreta123!\"}", null);
        assertEquals(201, registered.statusCode()); String access = text(registered.body(), "accessToken");
        long id = number(registered.body(), "id");
        assertEquals(403, call("GET", "/api/v1/orders", null, access).statusCode());
        assertEquals(200, call("PATCH", "/api/v1/users/" + id + "/role", "{\"role\":\"VENDEDOR\"}", admin()).statusCode());
        assertEquals(200, call("GET", "/api/v1/orders", null, access).statusCode());
        assertEquals(403, call("GET", "/api/v1/users", null, access).statusCode());
    }
}

package cl.duoc.veterinaria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Pruebas de punta a punta: levantan la API completa (H2 en memoria + Flyway)
 * en un puerto aleatorio y la llaman por HTTP, igual que lo hace el frontend.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTests {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    // ---------- utilidades ----------

    private HttpResponse<String> call(String method, String path, String json, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        if (json != null) {
            b.header("Content-Type", "application/json");
            b.method(method, HttpRequest.BodyPublishers.ofString(json));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private String login(String email, String password) throws Exception {
        HttpResponse<String> res = call("POST", "/api/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
        assertEquals(200, res.statusCode(), res.body());
        String token = field(res.body(), "accessToken");
        assertNotNull(token);
        return token;
    }

    // ---------- pruebas ----------

    @Test
    void healthEsPublico() throws Exception {
        HttpResponse<String> res = call("GET", "/api/health", null, null);
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"ok\":true"));
    }

    @Test
    void catalogoEsPublicoYTieneLosProductosIniciales() throws Exception {
        HttpResponse<String> res = call("GET", "/api/products", null, null);
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("Alimento perro adulto"));
        assertTrue(res.body().contains("\"category\":\"Salud\""));

        HttpResponse<String> cats = call("GET", "/api/categories", null, null);
        assertEquals(200, cats.statusCode());
        assertTrue(cats.body().contains("Higiene"));
    }

    @Test
    void loginIncorrectoDevuelve401ConMensaje() throws Exception {
        HttpResponse<String> res = call("POST", "/api/auth/login",
                "{\"email\":\"admin@sanmarcos.cl\",\"password\":\"incorrecta\"}", null);
        assertEquals(401, res.statusCode());
        assertTrue(res.body().contains("\"error\""));
    }

    @Test
    void registroCreaClienteYMeDevuelveElUsuario() throws Exception {
        HttpResponse<String> res = call("POST", "/api/auth/register",
                "{\"name\":\"Ana Pérez\",\"email\":\"ana@test.cl\",\"password\":\"Secreta1!\"}", null);
        assertEquals(201, res.statusCode(), res.body());
        assertTrue(res.body().contains("\"role\":\"CLIENTE\""));
        assertNotNull(field(res.body(), "refreshToken"));

        HttpResponse<String> me = call("GET", "/api/auth/me", null, field(res.body(), "accessToken"));
        assertEquals(200, me.statusCode());
        assertTrue(me.body().contains("ana@test.cl"));

        HttpResponse<String> dup = call("POST", "/api/auth/register",
                "{\"name\":\"Ana\",\"email\":\"ana@test.cl\",\"password\":\"Secreta1!\"}", null);
        assertEquals(409, dup.statusCode());
    }

    @Test
    void refreshEntregaTokensNuevos() throws Exception {
        HttpResponse<String> res = call("POST", "/api/auth/login",
                "{\"email\":\"cliente@sanmarcos.cl\",\"password\":\"Cliente123!\"}", null);
        String refresh = field(res.body(), "refreshToken");
        HttpResponse<String> renewed = call("POST", "/api/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}", null);
        assertEquals(200, renewed.statusCode());
        assertNotNull(field(renewed.body(), "accessToken"));

        // Un accessToken no sirve como refreshToken
        String access = field(res.body(), "accessToken");
        HttpResponse<String> wrong = call("POST", "/api/auth/refresh", "{\"refreshToken\":\"" + access + "\"}", null);
        assertEquals(401, wrong.statusCode());
    }

    @Test
    void rutasProtegidasSegunRol() throws Exception {
        assertEquals(401, call("GET", "/api/orders/mine", null, null).statusCode());
        assertEquals(401, call("GET", "/api/users", null, "token-falso").statusCode());

        String cliente = login("cliente@sanmarcos.cl", "Cliente123!");
        assertEquals(200, call("GET", "/api/orders/mine", null, cliente).statusCode());
        assertEquals(403, call("GET", "/api/users", null, cliente).statusCode());
        assertEquals(403, call("GET", "/api/orders", null, cliente).statusCode());
        assertEquals(403, call("DELETE", "/api/products/1", null, cliente).statusCode());

        String admin = login("admin@sanmarcos.cl", "Admin123!");
        assertEquals(200, call("GET", "/api/users", null, admin).statusCode());
        assertEquals(200, call("GET", "/api/orders", null, admin).statusCode());
    }

    @Test
    void adminCreaEditaYEliminaProducto() throws Exception {
        String admin = login("admin@sanmarcos.cl", "Admin123!");
        String body = "{\"name\":\"Collar antipulgas\",\"category\":\"Salud\",\"price\":5990,\"stock\":5,"
                + "\"offer\":false,\"description\":\"Protección por 8 meses.\",\"emoji\":\"🐾\"}";
        HttpResponse<String> created = call("POST", "/api/products", body, admin);
        assertEquals(201, created.statusCode(), created.body());
        Matcher id = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(created.body());
        assertTrue(id.find());

        HttpResponse<String> updated = call("PUT", "/api/products/" + id.group(1), body.replace("5990", "4990"), admin);
        assertEquals(200, updated.statusCode());
        assertTrue(updated.body().contains("4990"));

        HttpResponse<String> invalid = call("POST", "/api/products", body.replace("Salud", "Juguetes"), admin);
        assertEquals(400, invalid.statusCode());

        assertEquals(204, call("DELETE", "/api/products/" + id.group(1), null, admin).statusCode());
        assertEquals(404, call("GET", "/api/products/" + id.group(1), null, null).statusCode());
    }

    @Test
    void pedidoCalculaTotalEnServidorYValidaStock() throws Exception {
        String cliente = login("cliente@sanmarcos.cl", "Cliente123!");
        // Producto 4 = Snack dental, $4.290
        String order = "{\"name\":\"Cliente Demo\",\"email\":\"cliente@sanmarcos.cl\",\"address\":\"Av. Siempre Viva 123\","
                + "\"delivery\":\"Retiro en veterinaria\",\"payment\":\"Transferencia\","
                + "\"items\":[{\"productId\":4,\"quantity\":2}]}";
        HttpResponse<String> res = call("POST", "/api/orders", order, cliente);
        assertEquals(201, res.statusCode(), res.body());
        assertTrue(res.body().contains("\"total\":8580"), res.body());
        assertTrue(res.body().contains("\"status\":\"CONFIRMADO\""));

        String tooMany = order.replace("\"quantity\":2", "\"quantity\":999");
        assertEquals(409, call("POST", "/api/orders", tooMany, cliente).statusCode());

        String empty = order.replace("[{\"productId\":4,\"quantity\":2}]", "[]");
        assertEquals(400, call("POST", "/api/orders", empty, cliente).statusCode());

        HttpResponse<String> mine = call("GET", "/api/orders/mine", null, cliente);
        assertTrue(mine.body().contains("Snack dental"));
    }

    @Test
    void cancelarPedidoRepondeStock() throws Exception {
        String cliente = login("cliente@sanmarcos.cl", "Cliente123!");
        String admin = login("admin@sanmarcos.cl", "Admin123!");
        String before = call("GET", "/api/products/2", null, null).body();
        int stockBefore = Integer.parseInt(before.replaceAll(".*\"stock\":(\\d+).*", "$1"));

        String order = "{\"name\":\"Cliente Demo\",\"email\":\"cliente@sanmarcos.cl\",\"address\":\"Calle 1\","
                + "\"items\":[{\"productId\":2,\"quantity\":3}]}";
        HttpResponse<String> created = call("POST", "/api/orders", order, cliente);
        assertEquals(201, created.statusCode(), created.body());
        Matcher id = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(created.body());
        assertTrue(id.find(), created.body());

        HttpResponse<String> cancel = call("PATCH", "/api/orders/" + id.group(1) + "/status", "{\"status\":\"CANCELADO\"}", admin);
        assertEquals(200, cancel.statusCode(), cancel.body());

        String after = call("GET", "/api/products/2", null, null).body();
        int stockAfter = Integer.parseInt(after.replaceAll(".*\"stock\":(\\d+).*", "$1"));
        assertEquals(stockBefore, stockAfter);

        // Un pedido cancelado ya no cambia de estado
        assertEquals(409, call("PATCH", "/api/orders/" + id.group(1) + "/status", "{\"status\":\"DESPACHADO\"}", admin).statusCode());
    }
}

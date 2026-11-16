package cl.duoc.veterinaria.dto;

import java.util.Map;

/**
 * Formato único de error: { "error": "mensaje", "fields": { "campo": "detalle" } }.
 * El frontend (src/services/api.js) muestra "error" y usa "fields" en formularios.
 */
public record ErrorResponse(String error, Map<String, String> fields) {

    public ErrorResponse(String error) {
        this(error, null);
    }
}

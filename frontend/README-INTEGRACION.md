# Integración con el backend Spring Boot (EP3)

El frontend consume la API REST del repositorio [backend_veterinaria](https://github.com/Roselin2831/backend_veterinaria).

## Ejecutar

1. Levanta el backend (ver su README) → http://localhost:8080/swagger-ui.html
2. En esta carpeta: `npm install` y `npm run dev` → http://localhost:5173
3. Usuarios de prueba: `admin@sanmarcos.cl / Admin123!` · `cliente@sanmarcos.cl / Cliente123!`

La URL de la API se cambia con la variable `VITE_API_URL` (copia `.env.example` como `.env`).

## Dónde está cada parte

| Archivo | Responsabilidad |
|---|---|
| `src/services/api.js` | Cliente HTTP único: agrega `Authorization: Bearer`, renueva el token y convierte errores |
| `src/services/session.js` | Guarda y lee la sesión (tokens + usuario) en `localStorage` |
| `src/services/*Service.js` | Llamadas por recurso: auth, productos, pedidos, usuarios |
| `src/context/AuthContext.jsx` | Estado de sesión; al recargar la valida con `GET /api/auth/me` |
| `src/context/ShopContext.jsx` | Catálogo desde la API y carrito en `localStorage` |
| `src/components/ProtectedRoute.jsx` | Rutas protegidas por sesión y rol |
| `src/pages/MyOrders.jsx`, `Forbidden.jsx` | Mis pedidos y página de acceso restringido |

## Rutas y permisos

| Ruta | Visitante | CLIENTE | ADMIN |
|---|---|---|---|
| `/`, `/categorias`, `/ofertas`, `/carrito` | ✓ | ✓ | ✓ |
| `/checkout`, `/resultado/:status`, `/mis-pedidos` | → login | ✓ | ✓ |
| `/admin` | → login | → acceso restringido | ✓ |

Notas:
- `src/data/mockDb.js` ya no lo usa la aplicación; se mantiene solo para las pruebas unitarias de la etapa anterior.
- La carpeta `backend/` (Node.js) fue un prototipo previo; el backend oficial de la evaluación es el de Spring Boot.

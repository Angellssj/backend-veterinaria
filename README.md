# Backend · Veterinaria San Marcos (Spring Boot)

API REST de la tienda **Veterinaria San Marcos** — DSY1104 Desarrollo Fullstack II · Duoc UC · Equipo ROANG.
La consume el frontend React del repositorio `VETERINARIA_SAN_MARCOS`.

**Stack:** Java 21 · Spring Boot 4 · Spring Security + JWT · Spring Data JPA · Flyway · H2 (local) / MySQL (AWS RDS) · Swagger (springdoc).

## Ejecutar en local

Requisito: **JDK 21** instalado (`java -version`).

```bash
cd veterinaria
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

- API: http://localhost:8080/api/health
- Swagger: http://localhost:8080/swagger-ui.html
- Consola H2: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:veterinaria`, usuario `sa`, sin contraseña)

En local usa H2 en memoria: cada vez que se reinicia, la base vuelve al estado inicial.

Usuarios de prueba (se crean solos al arrancar):

| Correo | Contraseña | Rol |
|---|---|---|
| admin@sanmarcos.cl | Admin123! | ADMIN |
| cliente@sanmarcos.cl | Cliente123! | CLIENTE |

## Pruebas y empaquetado

```bash
./mvnw test             # pruebas de integración (levantan la API completa con H2)
./mvnw clean package    # genera target/veterinaria.jar (el archivo que se sube a AWS)
```

## Estructura

```text
veterinaria/src/main/java/cl/duoc/veterinaria/
  config/       DataInitializer (usuarios demo), OpenApiConfig (Swagger)
  controller/   Endpoints REST
  dto/          Objetos de entrada/salida (records)
  exception/    ApiException + manejador global de errores
  model/        Entidades JPA: User, Category, Product, Order, OrderItem
  repository/   Repositorios Spring Data
  security/     JWT, filtro de autenticación, reglas de acceso y CORS
  service/      Lógica de negocio
veterinaria/src/main/resources/
  application.properties        Configuración local (H2)
  application-prod.properties   Configuración AWS (MySQL, variables de entorno)
  db/migration/                 Esquema y datos iniciales (Flyway)
```

## Endpoints

Autenticación con cabecera `Authorization: Bearer <accessToken>`.
Errores siempre en formato `{ "error": "mensaje", "fields": { "campo": "detalle" } }`.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/health` | público | Estado del servicio |
| POST | `/api/auth/register` | público | `{ name, email, password }` → `{ accessToken, refreshToken, user }` (rol CLIENTE) |
| POST | `/api/auth/login` | público | `{ email, password }` → `{ accessToken, refreshToken, user }` |
| POST | `/api/auth/refresh` | público | `{ refreshToken }` → tokens nuevos |
| GET | `/api/auth/me` | sesión | Usuario actual (rol leído desde la base) |
| GET | `/api/products` | público | Filtros opcionales `?category=Salud&offer=true&q=perro` |
| GET | `/api/products/{id}` | público | Detalle |
| GET | `/api/categories` | público | Categorías |
| POST | `/api/products` | ADMIN | Crear producto |
| PUT | `/api/products/{id}` | ADMIN | Editar producto |
| DELETE | `/api/products/{id}` | ADMIN | Eliminar (lógico: se oculta del catálogo, se conserva en pedidos) |
| POST | `/api/orders` | sesión | `{ name, email, address, delivery, payment, items: [{ productId, quantity }] }` |
| GET | `/api/orders/mine` | sesión | Pedidos del usuario |
| GET | `/api/orders` | ADMIN | Todos los pedidos |
| PATCH | `/api/orders/{id}/status` | ADMIN | `{ status }`: CONFIRMADO, EN_PREPARACION, DESPACHADO, ENTREGADO, CANCELADO |
| GET | `/api/users` | ADMIN | Usuarios |
| PATCH | `/api/users/{id}/role` | ADMIN | `{ role }`: CLIENTE o ADMIN (no el propio) |

Reglas de negocio:
- El **precio y el total del pedido los calcula el servidor**; el stock se descuenta al confirmar y se responde `409` si no alcanza (la fila del producto se bloquea durante la compra para evitar sobreventa).
- Al **cancelar** un pedido el stock vuelve al inventario; un pedido cancelado ya no cambia de estado.
- Contraseñas con **BCrypt**. accessToken de 15 minutos y refreshToken de 7 días.

## Variables de entorno (producción)

| Variable | Ejemplo | Uso |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Activa MySQL y desactiva la consola H2 |
| `PORT` | `5000` | Puerto (Elastic Beanstalk espera 5000) |
| `DB_URL` | `jdbc:mysql://<endpoint>:3306/veterinaria?createDatabaseIfNotExist=true&serverTimezone=UTC` | Base RDS |
| `DB_USERNAME` / `DB_PASSWORD` | `admin` / `****` | Credenciales RDS |
| `JWT_SECRET` | 32+ caracteres aleatorios | Firma de tokens (obligatoria en prod) |
| `APP_CORS_ORIGINS` | `http://<bucket>.s3-website-us-east-1.amazonaws.com` | URL del frontend (varias separadas por coma) |
| `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` | opcional | Cambia el admin inicial |
| `APP_SEED_DEMO_CLIENT` | `true` / `false` | Crear o no el cliente demo |

## Despliegue en AWS

Paso a paso en [`DEPLOY-AWS.md`](DEPLOY-AWS.md): RDS MySQL + Elastic Beanstalk (API) + S3 (frontend).

# Especificación de requisitos de Veterinaria San Marcos

Versión 1.1. Fecha de revisión: 22 de noviembre de 2026.

## Propósito y alcance

La tienda permite consultar productos veterinarios, preparar un carrito y registrar pedidos. La administración mantiene el inventario y los roles. El vendedor consulta productos y pedidos sin modificar registros. Esta especificación describe la tienda evaluada por la pauta EP3 adjunta.

El documento Forma A incluido en el frontend describe citas y fichas clínicas. Ese alcance no está implementado y requiere confirmación del profesor. El sistema actual es una aplicación Spring Boot por capas, no un conjunto de microservicios.

## Actores y permisos

| Operación | Visitante | CLIENTE | VENDEDOR | ADMIN |
|---|---|---|---|---|
| Consultar catálogo y detalle | Sí | Sí | Sí | Sí |
| Registrarse e iniciar sesión | Sí | Sí | Sí | Sí |
| Crear pedido y consultar pedidos propios | No | Sí | No | Sí |
| Consultar todos los pedidos y su detalle | No | No | Sí | Sí |
| Crear, editar y eliminar productos | No | No | No | Sí |
| Modificar estado de pedidos | No | No | No | Sí |
| Listar usuarios y asignar roles | No | No | No | Sí |

El rol de un registro público siempre es CLIENTE. El administrador asigna VENDEDOR o ADMIN a otros usuarios y no puede cambiar su propio rol. El vendedor recibe un panel de consulta sin opciones de compra, usuarios ni edición. Las restricciones se verifican nuevamente en el backend.

## Requisitos funcionales y aceptación

| ID | Requisito | Criterio de aceptación |
|---|---|---|
| RF01 | Consultar productos activos, categorías, ofertas y búsqueda | La API devuelve datos persistidos y un producto eliminado deja de aparecer |
| RF02 | Mantener productos | ADMIN puede crear, consultar, editar y eliminar lógicamente un producto |
| RF03 | Persistir precio, stock, descripción, categoría y foto | La edición sobrevive a recargas y las URL de foto admiten HTTPS o rutas del catálogo |
| RF04 | Registrar e iniciar sesión | Un registro válido crea CLIENTE con contraseña BCrypt; duplicados devuelven 409 |
| RF05 | Mantener la sesión al recargar | Se recuperan tokens y se valida el usuario con GET /api/v1/auth/me antes de mostrar rutas protegidas |
| RF06 | Renovar y cerrar sesiones | Un refresh utilizado se rechaza; logout revoca la sesión en la base de datos |
| RF07 | Registrar un pedido | El servidor calcula precios y total, valida existencias y descuenta stock dentro de una transacción |
| RF08 | Consultar pedidos propios | CLIENTE solo recibe su historial en /orders/mine y no el listado global |
| RF09 | Consultar productos y pedidos como vendedor | VENDEDOR ve lista y detalle, pero recibe 403 si intenta escribir o consultar usuarios |
| RF10 | Gestionar estados | ADMIN cambia el estado. Cancelar repone existencias una sola vez; entregados y cancelados son finales |
| RF11 | Asignar roles | Un cambio de rol se aplica en la siguiente solicitud aunque el JWT haya sido emitido antes |
| RF12 | Documentar la API | Swagger muestra rutas /api/v1, DTO de entrada y salida y autenticación Bearer |

## Reglas de negocio

Los precios se expresan en pesos chilenos enteros. Se acepta stock cero. Cada línea del pedido exige una cantidad de 1 a 999 y un pedido admite hasta 50 líneas. Si un producto se repite, sus cantidades se suman antes de validar existencias.

El frontend envía identificadores y cantidades. El servidor toma los precios de la base de datos, por lo que cambiar un total en el navegador no altera el total registrado. Los bloqueos de inventario se toman en orden de identificador para reducir interbloqueos.

La eliminación de productos es lógica para conservar las referencias del historial. Los pedidos conservan el nombre y precio del momento de la compra. La cancelación bloquea el pedido y sus productos para evitar reposiciones duplicadas.

## Requisitos no funcionales

- Java 21, Spring Boot, Spring Data JPA, Spring Security y JWT. React 18, React Router, Bootstrap y Vite en el frontend.
- MySQL 8.4 en el despliegue y H2 en memoria para desarrollo y pruebas locales. Flyway administra el esquema, Hibernate lo valida.
- Errores de API en JSON con códigos 400, 401, 403, 404 y 409 según corresponda, sin devolver contraseñas ni trazas internas.
- Tokens de acceso de 15 minutos y renovación de 7 días. Los secretos de renovación se guardan como hashes SHA-256 y se rotan en cada uso.
- Formularios con etiquetas, estados de carga y mensajes de error. Navegación adaptable a móvil y escritorio.
- Configuración mediante variables de entorno. No publicar archivos .env ni credenciales reales en GitHub.

## Datos y relaciones

Usuarios tiene muchos pedidos y sesiones. Categorías tiene muchos productos. Pedidos tiene muchos detalles y cada detalle referencia un producto. Login sessions registra la revocación y el hash del token de renovación. El diagrama y las migraciones están explicados en INTEGRACION.md.

## Límites

El pago es una selección registrada, sin pasarela bancaria. No se generan boletas tributarias ni se conecta con el SII. H2 en memoria pierde los datos al detenerse. La aplicación utiliza tokens en localStorage: ante una vulnerabilidad XSS podrían ser leídos, por lo que un despliegue productivo requiere medidas adicionales y HTTPS. No se afirma que estos módulos cubran las citas, fichas clínicas o microservicios del documento Forma A.

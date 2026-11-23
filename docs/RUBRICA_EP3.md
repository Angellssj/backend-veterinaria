# Evidencia para la rúbrica EP3

La pauta distribuye un 40% al encargo grupal y un 60% a la presentación individual. Esta matriz enlaza la evidencia de la tienda implementada y permite preparar la demostración. No sustituye la evaluación del profesor.

| Indicador del encargo | Peso | Evidencia para revisar y demostrar |
|---|---:|---|
| Backend, base de datos, modelo y lógica | 8% | Entidades JPA, migraciones V1 a V4, servicios transaccionales, inventario y pruebas sobre MySQL |
| CRUD REST y Swagger | 8% | GET, POST, PUT y DELETE de productos en /api/v1 y esquema OpenAPI |
| Integración frontend y backend | 6% | Servicios fetch, catálogo desde API, pedido con total calculado en servidor y pruebas Playwright |
| Autenticación y roles JWT | 6% | BCrypt, access/refresh separados, renovación rotativa, revocación y validación de roles desde la base |
| Gestión y persistencia de sesión | 6% | AuthContext, /auth/me al recargar, renovación compartida, errores de red y logout |
| Restricciones de interfaz | 6% | ProtectedRoute, panel VENDEDOR, ADMIN y pruebas con roles distintos |

## Archivos de entrega

- [ERS](ERS.md).
- [Documento de integración](INTEGRACION.md).
- [Documentación de API](Documentacion_API.xlsx).
- [Manual con capturas](MANUAL_USUARIO.md) y [versión PDF](Manual_Usuario.pdf).
- Código de backend y frontend en sus repositorios públicos y en los comprimidos de entrega.
- Capturas de ejecución en `capturas/` y pruebas reproducibles en CI.

## Guion de demostración de 15 minutos

| Tiempo | Demostración | Explicación técnica |
|---|---|---|
| 0 a 2 min | Abrir sistema y modelo | React, Nginx, Spring Boot, MySQL, JPA y migraciones |
| 2 a 5 min | ADMIN crea, edita y elimina producto | DTO, validación, controlador, servicio, repositorio y eliminación lógica |
| 5 a 8 min | CLIENTE compra y consulta su pedido | ID y cantidad, precio del servidor, transacción y validación de stock |
| 8 a 10 min | VENDEDOR consulta productos y pedidos | Menús por rol y rechazo de escrituras por la API |
| 10 a 12 min | Recargar, renovar y cerrar sesión | Firma JWT, expiración, refresh de un uso y revocación |
| 12 a 15 min | Mostrar Swagger y pruebas | Contrato REST, códigos de error y evidencia de integración |

## Preguntas que cada integrante debe poder responder

1. ¿Por qué se usa DTO en lugar de devolver las entidades JPA?
2. ¿Por qué los precios y el total se calculan en el backend?
3. ¿Qué protege una transacción y para qué se bloquea el stock?
4. ¿Qué diferencia hay entre 401 y 403?
5. ¿Por qué ocultar un botón no basta para autorizar una operación?
6. ¿Cómo se detecta un JWT vencido y qué ocurre con un refresh ya utilizado?
7. ¿Qué cambia al recargar la página y por qué se llama a /auth/me?
8. ¿Qué persiste en el volumen MySQL y qué información contiene el archivo .env?
9. ¿Qué verifica una prueba unitaria, una prueba HTTP y una prueba de navegador?
10. ¿Cuáles son los límites actuales respecto de pagos, SII, seguridad productiva y caso clínico?

Cada integrante debe conocer los cambios y explicar la evidencia que realmente desarrolló o revisó. La autoría y la distribución del trabajo deben reflejar el trabajo real del equipo.

## Alcance que requiere confirmación

La pauta adjunta corresponde a una tienda. El documento Forma A del frontend pide además citas, fichas clínicas, mapas y microservicios con bases independientes. Esas funciones no están implementadas en esta rama y no se afirma que estén cubiertas.

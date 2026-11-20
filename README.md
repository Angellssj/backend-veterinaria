# Veterinaria San Marcos

Proyecto organizado en dos repositorios hermanos, siguiendo la estructura de
despliegue de referencia:

```text
proyecto/
├── backend/   Compose y configuración; la API está en veterinaria/
└── frontend/  React · Vite · Nginx
```

El backend usa Spring Boot 4 · Java 21 · MySQL · Flyway. El Compose está en la raíz
del repositorio backend, construye la API desde `veterinaria/` y el frontend desde
la carpeta hermana `../frontend`. El navegador llama a la API por el puerto 8080.

## Ejecutar con Docker

Requisitos: Docker Engine y Docker Compose v2.

En PowerShell:

```powershell
Copy-Item .env.example .env
notepad .env
docker compose -f docker-compose.aws.yml up --build -d
```

Ejecuta estos comandos desde la carpeta del repositorio backend. Configura en `.env`
una IP pública (para local, `localhost`), puertos y
contraseñas nuevas para MySQL, root de MySQL, administrador y JWT. Para generar
secretos hexadecimales en Linux:

```bash
openssl rand -hex 32
openssl rand -hex 48
```

Abre <http://localhost/>. La API se expone en <http://localhost:8080/api/health> y Swagger en
<http://localhost:8080/swagger-ui.html>. El puerto 3306 no se publica.

## Base de datos y migraciones

MySQL 8.4 conserva los datos en el volumen `mysql_data`. Flyway aplica las
migraciones versionadas de `veterinaria/src/main/resources/db/migration`; Hibernate
valida el esquema y no lo modifica.

```powershell
docker compose -f docker-compose.aws.yml ps
docker compose -f docker-compose.aws.yml logs -f backend
docker compose -f docker-compose.aws.yml logs -f mysql
docker compose -f docker-compose.aws.yml down
```

`down` conserva los datos. **No uses `down -v`** salvo que quieras eliminar el volumen
y perder la base.

## AWS EC2

Sigue [DEPLOY-EC2.md](DEPLOY-EC2.md) para crear la instancia, configurar el Security
Group y ejecutar Docker Compose. Clona el repositorio frontend como carpeta hermana
`frontend` junto a `backend`, ya que Compose usa `../frontend` como contexto.

La aplicación de ejemplo usa HTTP. No introduzcas credenciales reales hasta configurar
HTTPS y proteger los puertos de la API.

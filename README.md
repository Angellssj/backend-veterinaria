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
la carpeta hermana `../frontend`. Nginx (contenedor frontend) sirve la web y reenvía
`/api` al backend, así que todo funciona por el puerto 80 sin depender de la IP pública.

## Ejecutar con Docker

Requisitos: Docker Engine y Docker Compose v2.

En PowerShell:

```powershell
Copy-Item .env.example .env
notepad .env
docker compose -f docker-compose.aws.yml up --build -d
```

Ejecuta estos comandos desde la carpeta del repositorio backend. Configura en `.env`
puertos y contraseñas nuevas para MySQL, root de MySQL, administrador y JWT. Para generar
secretos hexadecimales en Linux:

```bash
openssl rand -hex 32
openssl rand -hex 48
```

Abre <http://localhost/>. La API responde en <http://localhost/api/health> y Swagger en
<http://localhost/swagger-ui.html>. El 8080 solo escucha en 127.0.0.1 y el 3306 no se publica.

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

Sigue [DEPLOY-EC2.md](DEPLOY-EC2.md). En resumen, con el puerto 80 abierto en el
Security Group:

```bash
mkdir -p ~/vetsm-app && cd ~/vetsm-app
git clone https://github.com/Angellssj/backend-veterinaria.git backend
bash backend/deploy/ec2-deploy.sh
```

El script clona el frontend como carpeta hermana `frontend`, genera `.env` y levanta todo.

La aplicación de ejemplo usa HTTP. No introduzcas credenciales reales hasta configurar
HTTPS y proteger los puertos de la API.

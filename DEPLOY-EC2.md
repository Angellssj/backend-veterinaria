# Despliegue de Veterinaria San Marcos en AWS EC2

La estructura sigue la de referencia: `backend` y `frontend` son carpetas hermanas.
`backend/docker-compose.aws.yml` construye la API Spring Boot desde
`backend/veterinaria/`, MySQL y el frontend React desde `../frontend`, servido por Nginx.

```text
Navegador ── :80 ──► Nginx + React
Navegador ── :8080 ► API Spring Boot ──► MySQL 8.4
                                     └──► Flyway migra el esquema
```

La API se publica directamente en el puerto 8080, por lo que `VITE_API_URL` se fija
durante la compilación como `http://PUBLIC_IP:SPRING_PORT/api`. El backend admite como
origen CORS el origen del frontend.

## 1. Crear la instancia EC2

En AWS Academy, crea una instancia Ubuntu Server 22.04/24.04 x86_64. La guía de
referencia propone `t2.micro`/`t3.micro`; compilar Java y Node en una instancia de 1 GiB
puede agotar memoria. Usa al menos 2 GiB si el laboratorio lo permite.

Configura el Security Group:

| Tipo | Puerto | Origen | Motivo |
|---|---:|---|---|
| SSH | 22 | My IP | Administración |
| HTTP | 80 | `0.0.0.0/0` | Sitio React |
| Custom TCP | 8080 | `0.0.0.0/0` | API consumida por el navegador |

No abras 3306 (MySQL) ni 5173 (Vite). El puerto 8080 es necesario porque el navegador
accede directamente a la API; si no debe estar público, habrá que configurar HTTPS y
un proxy inverso para servir `/api` por el mismo origen.

## 2. Instalar Docker y Git

Conecta usando la clave `.pem`:

```bash
chmod 400 vetsm-key.pem
ssh -i vetsm-key.pem ubuntu@<IP_PUBLICA_EC2>
```

En la instancia:

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-v2 git openssl
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

Vuelve a iniciar sesión SSH para aplicar el grupo `docker`, luego verifica:

```bash
docker --version
docker compose version
```

## 3. Poner los repositorios como hermanos

Crea la carpeta de aplicación y clona **tus repositorios**, no los de ejemplo del
compañero:

```bash
mkdir -p ~/vetsm-app
cd ~/vetsm-app
git clone --branch deploy/docker-compose-mysql --single-branch https://github.com/Angellssj/backend-veterinaria.git backend
git clone --branch deploy/docker-compose-mysql --single-branch https://github.com/Roselin2831/VETERINARIA_SAN_MARCOS.git frontend
```

El Compose está en la raíz del backend; compila Java desde `./veterinaria` y usa
`../frontend` como contexto de build.
Verifica la disposición:

```text
~/vetsm-app/backend/docker-compose.aws.yml
~/vetsm-app/backend/veterinaria/Dockerfile
~/vetsm-app/frontend/Dockerfile
```

Ejecuta Compose desde `backend`. Para probar otra rama, clona ambos repositorios con
el mismo nombre de rama en el parámetro `--branch`.

## 4. Configurar el entorno

```bash
cd ~/vetsm-app/backend
cp .env.example .env
nano .env
```

Completa los valores:

```env
PUBLIC_IP=<IP_PUBLICA_EC2>
SPRING_PORT=8080
HTTP_PORT=80
MYSQL_PASSWORD=<CLAVE_MYSQL_NUEVA>
MYSQL_ROOT_PASSWORD=<CLAVE_ROOT_MYSQL_NUEVA>
JWT_SECRET=<SECRETO_ALEATORIO_DE_32_BYTES_O_MAS>
APP_ADMIN_EMAIL=admin@sanmarcos.cl
APP_ADMIN_PASSWORD=<CLAVE_ADMIN_NUEVA>
APP_SEED_DEMO_CLIENT=false
CORS_ALLOWED_ORIGINS=
```

Genera secretos con `openssl rand -hex 32` (contraseñas) y
`openssl rand -hex 48` (JWT). No reutilices valores ilustrativos ni subas `.env`.
Restringe sus permisos:

```bash
chmod 600 .env
```

Si `HTTP_PORT` no es 80, define también `CORS_ALLOWED_ORIGINS` como
`http://<IP_PUBLICA_EC2>:<HTTP_PORT>`. El valor vacío usa `http://PUBLIC_IP`.

## 5. Construir y desplegar

Desde la carpeta backend:

```bash
docker compose -f docker-compose.aws.yml config
docker compose -f docker-compose.aws.yml up --build -d
```

La primera compilación puede demorar. MySQL usa un volumen persistente y debe pasar
su healthcheck antes de iniciar la API. Flyway ejecuta las migraciones versionadas de
`veterinaria/src/main/resources/db/migration`; Hibernate valida las tablas.

## 6. Verificar

```bash
docker compose -f docker-compose.aws.yml ps
docker compose -f docker-compose.aws.yml logs -f backend
docker compose -f docker-compose.aws.yml logs -f frontend
docker compose -f docker-compose.aws.yml logs -f mysql
```

Desde un navegador:

- Frontend: `http://<IP_PUBLICA_EC2>/`
- API health: `http://<IP_PUBLICA_EC2>:8080/api/health`
- Catálogo: `http://<IP_PUBLICA_EC2>:8080/api/products`
- Swagger: `http://<IP_PUBLICA_EC2>:8080/swagger-ui.html`

## 7. Datos y reinicios

Para detener o actualizar servicios sin eliminar datos:

```bash
docker compose -f docker-compose.aws.yml down
docker compose -f docker-compose.aws.yml up --build -d
```

`down` conserva el volumen MySQL. `down -v` elimina también la base de datos. El
volumen permanece en el disco de la instancia: haz backups fuera de EC2 para evitar
pérdida ante eliminación o fallo del disco.

## Límites de seguridad

Esta configuración es para la demostración del curso: la web y API usan HTTP. No
envíes contraseñas reales ni datos sensibles; añade HTTPS antes de usarla públicamente.
No publiques el puerto 3306. Si usas `t2.micro`/`t3.micro` y la compilación termina por
falta de memoria, detén servicios que no uses, agrega swap o cambia a una instancia
con más memoria.

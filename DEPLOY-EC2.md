# Despliegue de Veterinaria San Marcos en AWS EC2 (Docker)

`docker-compose.aws.yml` levanta tres contenedores: MySQL, la API Spring Boot y el
frontend React servido por Nginx. Nginx también reenvía `/api` al backend, así que
**todo entra por el puerto 80** y el frontend no depende de la IP pública de la instancia.

```text
Navegador ── :80 ──► Nginx ──► archivos React (/, /categorias, ...)
                         └──► /api, /swagger-ui ──► Spring Boot :8080 ──► MySQL 8.4
                                                            └──► Flyway migra el esquema
```

Se usan dos repositorios clonados como carpetas hermanas:

```text
~/vetsm-app/
├── backend/    https://github.com/Angellssj/backend-veterinaria
└── frontend/   https://github.com/Roselin2831/VETERINARIA_SAN_MARCOS
```

Como el frontend llama a `/api` (ruta relativa), si la IP de la EC2 cambia (por ejemplo,
al reiniciar el laboratorio de AWS Academy) **no hay que recompilar nada**: basta con
entrar con la IP nueva.

## 1. Crear la instancia EC2

- AMI: **Ubuntu Server 24.04 (o 22.04)**, x86_64.
- Tipo: `t3.small` (2 GiB) recomendado. `t2.micro`/`t3.micro` funciona porque el
  script crea 2 GiB de swap, pero la primera compilación tarda bastante más.
- Disco: 16 GiB o más (las imágenes de Maven, Node y MySQL ocupan espacio).
- Par de claves: descarga el `.pem` (por ejemplo `vetsm-key.pem`).

Security Group, reglas de entrada:

| Tipo | Puerto | Origen | Motivo |
|---|---:|---|---|
| SSH | 22 | My IP | Administración |
| HTTP | 80 | `0.0.0.0/0` | Sitio web + API (`/api`) |

No abras 3306 (MySQL) ni 8080: la API ya es accesible en `http://<IP>/api`.

## 2. Conectarse

Desde PowerShell, en la carpeta donde está la clave:

```powershell
ssh -i vetsm-key.pem ubuntu@<IP_PUBLICA_EC2>
```

Si `ssh` reclama que la clave tiene permisos demasiado abiertos:

```powershell
icacls vetsm-key.pem /inheritance:r /grant:r "$($env:USERNAME):R"
```

## 3. Desplegar

En la EC2:

```bash
sudo apt-get update && sudo apt-get install -y git
mkdir -p ~/vetsm-app && cd ~/vetsm-app
git clone https://github.com/Angellssj/backend-veterinaria.git backend
bash backend/deploy/ec2-deploy.sh
```

El script:

1. Instala Docker, Docker Compose y Git si faltan.
2. Clona el frontend en `~/vetsm-app/frontend` (y en las siguientes ejecuciones hace
   `git pull` de ambos repositorios).
3. Detiene el Nginx o el servicio `veterinaria` instalados fuera de Docker por el
   despliegue anterior, para liberar el puerto 80.
4. Crea 2 GiB de swap si la instancia no tiene.
5. Genera `backend/.env` con contraseñas y JWT aleatorios (solo la primera vez) y
   muestra la contraseña del administrador `admin@sanmarcos.cl`.
6. Construye las imágenes, levanta los contenedores, espera a que `/api/health`
   responda y muestra las URLs públicas.

Al terminar, abre en el navegador:

- Sitio: `http://<IP_PUBLICA_EC2>/`
- API: `http://<IP_PUBLICA_EC2>/api/health` y `/api/products`
- Swagger: `http://<IP_PUBLICA_EC2>/swagger-ui.html`

Para ver de nuevo la contraseña del administrador: `grep ADMIN ~/vetsm-app/backend/.env`.

## 4. Actualizar después de cambiar el código

Haz push a GitHub y luego, en la EC2:

```bash
bash ~/vetsm-app/backend/deploy/ec2-deploy.sh
```

Trae los cambios de ambos repositorios y reconstruye. La base de datos se conserva en
el volumen `mysql_data` y `.env` no se modifica.

## 5. Comandos útiles

Desde `~/vetsm-app/backend`:

```bash
docker compose -f docker-compose.aws.yml ps
docker compose -f docker-compose.aws.yml logs -f backend
docker compose -f docker-compose.aws.yml logs -f frontend
docker compose -f docker-compose.aws.yml restart backend
docker compose -f docker-compose.aws.yml down        # detiene y conserva los datos
```

**No uses `down -v`**: borra el volumen y pierdes la base de datos.

Los contenedores tienen `restart: unless-stopped`: si detienes y vuelves a iniciar la
instancia (o el laboratorio), Docker los levanta solos. Solo cambia la IP pública.

## 6. Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| El navegador no carga nada | Falta la regla HTTP 80 en el Security Group, o el navegador forzó `https://`: escribe `http://`. |
| La página carga pero dice "No se pudo conectar con el servidor" | El backend aún arranca o falló: `docker compose -f docker-compose.aws.yml logs backend`. |
| `Bind for 0.0.0.0:80 failed: port is already allocated` | Otro programa usa el puerto 80: `sudo ss -ltnp 'sport = :80'` y detenlo. |
| La compilación se corta con `Killed` / `exit code 137` | Falta memoria: revisa la swap con `free -h` o usa `t3.small`. |
| `permission denied ... docker.sock` | Cierra la sesión SSH y vuelve a entrar (grupo `docker`), o usa `sudo`. |
| Error de Flyway / tablas al arrancar | La base tiene un esquema anterior incompatible. Si son datos de prueba: `down -v` y vuelve a desplegar. |

## 7. Opcional: exponer la API en el puerto 8080

No hace falta para el sitio. Si quieres llamar a la API directamente por el 8080,
pon `SPRING_BIND=0.0.0.0` en `backend/.env`, abre el 8080 en el Security Group y vuelve
a ejecutar el script.

## Límites de seguridad

Esta configuración es para la demostración del curso: la web usa HTTP sin cifrar. No
envíes contraseñas reales ni datos sensibles; añade HTTPS (dominio + certificado) antes
de usarla públicamente. Haz backups de la base fuera de la EC2 si los datos importan.

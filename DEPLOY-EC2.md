# Despliegue de Veterinaria San Marcos en AWS EC2

`backend/docker-compose.aws.yml` levanta tres contenedores: MySQL, la API Spring Boot y
el frontend React servido por Nginx. Nginx también reenvía `/api` al backend, así que
**todo entra por el puerto 80** y el frontend no depende de la IP pública de la instancia.

```text
Navegador ── :80 ──► Nginx ──► archivos React (/, /categorias, ...)
                         └──► /api, /swagger-ui ──► Spring Boot :8080 ──► MySQL 8.4
                                                            └──► Flyway migra el esquema
```

Como el frontend llama a `/api` (ruta relativa), si la IP de la EC2 cambia (por ejemplo,
al reiniciar el laboratorio de AWS Academy) **no hay que recompilar nada**: solo entra
con la nueva IP.

## 1. Crear la instancia EC2

- AMI: **Ubuntu Server 24.04 (o 22.04)**, x86_64.
- Tipo: `t3.small` (2 GiB) recomendado. `t2.micro`/`t3.micro` funciona porque el
  script crea 2 GiB de swap, pero la primera compilación tarda bastante más.
- Disco: 16 GiB o más (las imágenes de Maven, Node y MySQL ocupan espacio).
- Par de claves: crea o descarga el `.pem` (por ejemplo `vetsm-key.pem`).

Security Group, reglas de entrada:

| Tipo | Puerto | Origen | Motivo |
|---|---:|---|---|
| SSH | 22 | My IP | Administración |
| HTTP | 80 | `0.0.0.0/0` | Sitio web + API (`/api`) |

No abras 3306 (MySQL) ni 8080: la API ya es accesible por `http://<IP>/api`.

## 2. Copiar el proyecto a la instancia

### Opción A: desde tu PC (Windows PowerShell)

Desde la carpeta `VETERINARIA` empaqueta el proyecto sin `node_modules`, `target`,
`dist` ni tu `.env` local:

```powershell
cd "$HOME\OneDrive\Escritorio\01 Universidad\VETERINARIA"
tar --exclude=node_modules --exclude=target --exclude=dist --exclude=.env -czf vetsm.tgz -C deploy-ec2 .
scp -i vetsm-key.pem vetsm.tgz ubuntu@<IP_PUBLICA_EC2>:~/
```

Si `scp` reclama que la clave tiene permisos demasiado abiertos, en PowerShell ejecuta:

```powershell
icacls vetsm-key.pem /inheritance:r /grant:r "$($env:USERNAME):R"
```

Luego, en la EC2:

```bash
ssh -i vetsm-key.pem ubuntu@<IP_PUBLICA_EC2>
mkdir -p ~/vetsm-app && tar -xzf ~/vetsm.tgz -C ~/vetsm-app
```

### Opción B: desde GitHub

Si subiste esta carpeta (con `backend/`, `frontend/` y `deploy/`) a un repositorio:

```bash
git clone <URL_DE_TU_REPOSITORIO> ~/vetsm-app
```

## 3. Desplegar (un solo comando)

```bash
cd ~/vetsm-app
bash deploy/ec2-deploy.sh
```

El script:

1. Instala Docker y Docker Compose si faltan.
2. Crea 2 GiB de swap si la instancia no tiene.
3. Genera `backend/.env` con contraseñas y JWT aleatorios (solo la primera vez) y
   muestra la contraseña del administrador `admin@sanmarcos.cl`.
4. Construye las imágenes y levanta los contenedores.
5. Espera a que `/api/health` responda y muestra las URLs públicas.

Al terminar, abre en el navegador:

- Sitio: `http://<IP_PUBLICA_EC2>/`
- API: `http://<IP_PUBLICA_EC2>/api/health` y `/api/products`
- Swagger: `http://<IP_PUBLICA_EC2>/swagger-ui.html`

Para ver la contraseña del administrador más tarde: `grep ADMIN ~/vetsm-app/backend/.env`.

## 4. Actualizar después de cambiar el código

Copia de nuevo el proyecto (paso 2, sin borrar `backend/.env` de la EC2) o haz `git pull`,
y vuelve a ejecutar:

```bash
cd ~/vetsm-app && bash deploy/ec2-deploy.sh
```

La base de datos se conserva en el volumen `mysql_data`.

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
| El navegador no carga nada | Falta la regla HTTP 80 en el Security Group, o usas `https://` en vez de `http://`. |
| La página carga pero dice "No se pudo conectar con el servidor" | El backend aún está arrancando o falló: `docker compose -f docker-compose.aws.yml logs backend`. |
| La compilación se corta con `Killed` / `exit code 137` | Falta memoria: confirma la swap con `free -h` o usa `t3.small`. |
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

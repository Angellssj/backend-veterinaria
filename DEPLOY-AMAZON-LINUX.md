# Despliegue en Amazon Linux con IP pública

Esta guía usa los dos repositorios y una sola instancia EC2. Docker ejecuta MySQL 8.4, Spring Boot y Nginx con React. El navegador entra por HTTP 80. Nginx reenvía `/api/v1`, Swagger y OpenAPI al backend.

## Preparar la instancia existente

En EC2, selecciona la instancia y abre **Conectar → EC2 Instance Connect** con el usuario `ec2-user`. Confirma el sistema con `cat /etc/os-release`.

En el Security Group asociado, agrega una regla de entrada **HTTP TCP 80 desde 0.0.0.0/0**. Conserva SSH 22 limitado al origen necesario para tu forma de conexión. MySQL 3306 y la API interna 8080 no necesitan acceso público. La instancia debe tener IP pública y salida a internet para descargar las dependencias.

## Descargar las versiones preparadas

Ejecuta en la terminal de la instancia. Estos comandos crean carpetas nuevas y no reemplazan instalaciones anteriores:

```bash
sudo yum install -y git
git clone --branch codex/mejoras-rubrica-ep3 https://github.com/Angellssj/backend-veterinaria.git ~/vetsm-backend
git clone --branch codex/mejoras-rubrica-ep3 https://github.com/Roselin2831/VETERINARIA_SAN_MARCOS.git ~/vetsm-frontend
cd ~/vetsm-backend
FRONTEND_DIR="$HOME/vetsm-frontend" bash deploy/ec2-deploy.sh
```

Si las mejoras ya están integradas en `main`, se puede omitir `--branch codex/mejoras-rubrica-ep3` en ambos comandos. Si las carpetas ya existen, revisa su contenido antes de usarlas. No las borres para repetir el despliegue.

## Qué hace el comando

1. Instala Docker, Compose y Buildx si faltan. Detecta Amazon Linux 2 y 2023 y verifica los checksums de los plugins descargados.
2. Genera `backend/.env` con contraseñas aleatorias y permisos de lectura restringidos. Conserva el archivo si ya existe.
3. Crea 2 GiB de swap únicamente en máquinas con poca memoria y sin swap. Usa un archivo propio y comprueba el espacio disponible.
4. Si MySQL ya está funcionando, genera un respaldo antes de actualizar.
5. Construye backend y frontend de manera secuencial, aplica las migraciones y espera a que los tres contenedores estén saludables.
6. Verifica la página, las rutas React, la consulta del catálogo en la base de datos, Swagger, el inicio de sesión de ADMIN y el cierre de esa sesión.

El primer despliegue puede tardar varios minutos. El resultado muestra las URL públicas. La comprobación final se hace desde el servidor: confirma también el acceso desde tu navegador para validar el Security Group y la red de AWS.

## Abrir el sistema

Sustituye `IP_PUBLICA_EC2` por la IP que aparece en la consola AWS:

- Frontend: `http://IP_PUBLICA_EC2/`
- API: `http://IP_PUBLICA_EC2/api/v1/products`
- Estado: `http://IP_PUBLICA_EC2/api/v1/health`
- Swagger: `http://IP_PUBLICA_EC2/swagger-ui.html`

Usuario administrador: `admin@sanmarcos.cl`. Consulta la contraseña únicamente en la terminal del servidor:

```bash
grep '^APP_ADMIN_PASSWORD=' ~/vetsm-backend/backend/.env
```

El despliegue desactiva las cuentas de demostración. Puedes registrar clientes desde la web y asignar VENDEDOR a una cuenta desde el panel de usuarios de ADMIN.

## Comprobar y diagnosticar

```bash
cd ~/vetsm-backend
docker compose -f backend/docker-compose.aws.yml --project-directory backend ps
docker compose -f backend/docker-compose.aws.yml --project-directory backend logs --tail=80 backend
docker compose -f backend/docker-compose.aws.yml --project-directory backend logs --tail=80 mysql
bash deploy/verificar.sh
```

Si aparece un error de permiso de Docker, agrega `sudo` delante de los comandos `docker`. El script de despliegue detecta ese caso automáticamente.

| Síntoma | Revisión |
|---|---|
| Funciona con curl en EC2, pero el navegador no conecta | IP pública, regla TCP 80, rutas de la subred y acceso a internet |
| Nginx devuelve 502 | Estado y logs del backend, variables de MySQL y migraciones |
| El frontend muestra error de conexión | La imagen debe construirse con `VITE_API_URL=/api/v1` |
| La compilación termina por falta de memoria | `free -h`, swap y espacio con `df -h` |
| MySQL rechaza la contraseña tras actualizar | Recuperar el `.env` original del volumen, sin generar credenciales nuevas |
| Flyway detecta una migración incompatible | Revisar `flyway_schema_history` y el respaldo. No borrar el volumen ni ejecutar repair sin analizar la diferencia |

## Actualizar conservando datos

```bash
git -C ~/vetsm-backend pull --ff-only
git -C ~/vetsm-frontend pull --ff-only
cd ~/vetsm-backend
FRONTEND_DIR="$HOME/vetsm-frontend" bash deploy/ec2-deploy.sh
```

Los datos permanecen en el volumen Docker `backend_mysql_data`. Guarda una copia del `.env` fuera del repositorio. Para crear un respaldo adicional ejecuta `bash deploy/backup-mysql.sh` y copia el archivo de `backups/` fuera de la instancia.

El alcance de estos comandos es publicar la aplicación por HTTP para la evaluación. Para utilizar cuentas o datos reales, configura HTTPS con un dominio y certificado antes de hacerlo. El sistema registra pedidos, sin cobrar tarjetas ni emitir documentos tributarios en el SII.

## Referencias técnicas

- Docker Compose y orden de arranque: https://docs.docker.com/compose/how-tos/startup-order/
- Instalación del plugin Compose: https://docs.docker.com/compose/install/linux/
- Docker en Amazon Linux: https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-docker.html

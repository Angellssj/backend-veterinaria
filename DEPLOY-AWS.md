# Despliegue en AWS · Veterinaria San Marcos

> **Alternativa no utilizada.** El despliegue real del proyecto es una EC2 con Docker
> Compose por el puerto 80: sigue [DEPLOY-EC2.md](DEPLOY-EC2.md). Esta guía (S3 +
> Elastic Beanstalk + RDS) se conserva solo como referencia.

Arquitectura (todo en la **misma región**, recomendado `us-east-1` — es la que usa AWS Academy):

```text
Navegador ──► S3 (sitio estático React)          http://<bucket>.s3-website-us-east-1.amazonaws.com
    │
    └────────► Elastic Beanstalk (API Spring Boot) http://<entorno>.us-east-1.elasticbeanstalk.com/api
                     │  (nginx :80 → Java :5000)
                     └──► RDS MySQL (privada, puerto 3306, solo accesible desde la API)
```

Orden recomendado (la base tarda ~10 min en crearse, así que se parte por ella):

| # | Paso | Dónde |
|---|---|---|
| 0 | Elegir nombres y generar el secreto JWT | tu PC |
| 1 | Crear la base de datos RDS MySQL | Consola AWS → RDS |
| 2 | Compilar el backend → `veterinaria.jar` | tu PC |
| 3 | Crear el entorno Elastic Beanstalk y subir el jar | Consola AWS → Elastic Beanstalk |
| 4 | Permitir que la API se conecte a RDS (grupo de seguridad) | Consola AWS → EC2 / RDS |
| 5 | Verificar la API | navegador |
| 6 | Compilar el frontend con la URL de la API | tu PC |
| 7 | Crear el bucket S3 y publicar el frontend | Consola AWS → S3 |
| 8 | Prueba completa | navegador |

---

## Paso 0 · Antes de empezar

1. **Requisitos en tu PC:** JDK 21 (`java -version`), Node 18+ (`node -v`).
2. **Prueba en local primero** (si falla en local, fallará en AWS):
   ```bash
   cd backend_veterinaria/veterinaria
   ./mvnw spring-boot:run            # Windows: mvnw.cmd spring-boot:run
   # en otra terminal:
   cd VETERINARIA_SAN_MARCOS
   npm install && npm run dev        # http://localhost:5173 → ingresa con admin@sanmarcos.cl / Admin123!
   ```
3. **Elige los nombres** y anótalos (los usarás varias veces):

   | Cosa | Ejemplo |
   |---|---|
   | Región | `us-east-1` (N. Virginia) |
   | Bucket S3 del frontend (único en todo AWS, minúsculas) | `veterinaria-san-marcos-front-roang` |
   | URL del frontend (se deduce del bucket) | `http://veterinaria-san-marcos-front-roang.s3-website-us-east-1.amazonaws.com` |
   | Contraseña maestra de RDS | una segura, sin `@`, `/`, `"` ni espacios |

4. **Genera el secreto JWT** (mínimo 32 caracteres):
   ```powershell
   # Windows PowerShell
   [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
   ```
   ```bash
   # Mac / Linux
   openssl rand -base64 48
   ```

> **AWS Academy (Learner Lab):** inicia el laboratorio (**Start Lab**, esperar el punto verde) y entra con **AWS**. Trabaja en `us-east-1`. Cuando un asistente pida un rol IAM, usa **LabRole** y como perfil de instancia **LabInstanceProfile** (no puedes crear roles nuevos). Al cerrar la sesión del laboratorio algunos recursos se detienen; al volver, revisa que todo esté en verde antes de presentar.

---

## Paso 1 · Base de datos RDS MySQL

Consola → **RDS** → **Crear base de datos**:

| Opción | Valor |
|---|---|
| Método de creación | Creación estándar |
| Motor | **MySQL** (versión 8.4 por defecto) |
| Plantilla | **Capa gratuita** (si no aparece: *Desarrollo y pruebas*) |
| Implementación | Instancia de base de datos Single-AZ |
| Identificador | `veterinaria-db` |
| Usuario maestro | `admin` |
| Administración de credenciales | **Autoadministrado** → escribe la contraseña maestra |
| Clase de instancia | `db.t3.micro` o `db.t4g.micro` |
| Almacenamiento | 20 GiB, **desactivar** escalado automático |
| Conectividad → Recurso de computación | No conectarse a un recurso EC2 (lo haremos en el paso 4) |
| VPC | la predeterminada (*Default VPC*) |
| **Acceso público** | **No** |
| Grupo de seguridad | Crear nuevo → `veterinaria-db-sg` |
| Configuración adicional → **Nombre de base de datos inicial** | `veterinaria` |
| Copias de seguridad automáticas | puedes desactivarlas para el proyecto |

Clic en **Crear base de datos**. Mientras se crea (~10 min) sigue con el paso 2.

Cuando el estado sea **Disponible**, entra a `veterinaria-db` → pestaña **Conectividad y seguridad** y copia el **Punto de enlace** (endpoint), por ejemplo:
`veterinaria-db.abc123xyz.us-east-1.rds.amazonaws.com`

Con él arma la URL JDBC:

```text
jdbc:mysql://veterinaria-db.abc123xyz.us-east-1.rds.amazonaws.com:3306/veterinaria?createDatabaseIfNotExist=true&serverTimezone=UTC
```

> No necesitas crear tablas a mano: al arrancar, **Flyway** ejecuta `V1__esquema_inicial.sql` y `V2__datos_iniciales.sql`, y la app crea los usuarios demo.

---

## Paso 2 · Compilar el backend

```bash
cd backend_veterinaria/veterinaria
./mvnw clean package        # Windows: mvnw.cmd clean package
```

Debe terminar con `BUILD SUCCESS` (también corre las pruebas). El archivo a subir queda en:

```text
backend_veterinaria/veterinaria/target/veterinaria.jar
```

---

## Paso 3 · Elastic Beanstalk (API)

Consola → **Elastic Beanstalk** → **Crear aplicación** (o *Crear entorno*).

**Paso 1 del asistente — Configurar entorno**

| Opción | Valor |
|---|---|
| Nivel | **Entorno de servidor web** |
| Nombre de la aplicación | `veterinaria-san-marcos` |
| Nombre del entorno | `veterinaria-api` |
| Dominio | `veterinaria-api` (comprueba disponibilidad) → quedará `veterinaria-api.us-east-1.elasticbeanstalk.com` |
| Plataforma | **Java** → rama **Corretto 21 running on 64bit Amazon Linux 2023** |
| Código de la aplicación | **Cargar el código** → etiqueta `v1` → archivo `veterinaria.jar` |
| Ajustes preestablecidos | **Instancia única (apta para la capa gratuita)** |

**Paso 2 — Acceso al servicio**

| Opción | Cuenta personal | AWS Academy |
|---|---|---|
| Rol de servicio | Crear y usar nuevo rol de servicio | **LabRole** |
| Perfil de instancia EC2 | `aws-elasticbeanstalk-ec2-role` (créalo si te lo pide) | **LabInstanceProfile** |
| Par de claves EC2 | opcional | `vockey` (opcional) |

**Paso 3 — Redes y base de datos:** VPC predeterminada, marca **IP pública** y al menos una subred. **No** habilites la base de datos de Beanstalk (ya creamos RDS aparte, así no se borra si eliminas el entorno).

**Paso 4 — Instancias y escalado:** tipo `t3.micro` (o `t3.small` si la app arranca lento). El resto por defecto.

**Paso 5 — Actualizaciones, monitoreo y registros** → al final, **Propiedades del entorno** → agrega:

| Nombre | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `PORT` | `5000` |
| `DB_URL` | la URL JDBC del paso 1 |
| `DB_USERNAME` | `admin` |
| `DB_PASSWORD` | la contraseña maestra de RDS |
| `JWT_SECRET` | el secreto generado en el paso 0 |
| `APP_CORS_ORIGINS` | la URL del frontend del paso 0, **sin `/` al final** |

(Opcional: `APP_ADMIN_PASSWORD` para no usar `Admin123!` en la nube. Si lo cambias, el texto "Demo: ..." de la pantalla de login quedará desactualizado.)

**Revisar → Enviar.** La creación tarda ~5 min. Es normal que la primera vez quede en **rojo/Degradado**: la API aún no puede llegar a la base. Eso se arregla en el paso 4.

---

## Paso 4 · Conectar la API con RDS (grupo de seguridad)

La base es privada: hay que permitir que **solo** la instancia de Beanstalk entre por el puerto 3306.

1. Consola → **EC2** → **Grupos de seguridad**. Busca el del entorno: se llama algo como `awseb-e-xxxxxxxx-stack-AWSEBSecurityGroup-...`. Copia su **ID** (`sg-0abc...`).
2. Abre el grupo **`veterinaria-db-sg`** → **Editar reglas de entrada** → **Agregar regla**:
   - Tipo: **MYSQL/Aurora** (puerto 3306)
   - Origen: **Personalizado** → pega el ID `sg-0abc...` del grupo de Beanstalk
   - Guardar.
   - Si existe una regla de entrada creada automáticamente con tu IP, puedes dejarla o quitarla; no se usa.
3. Vuelve a Elastic Beanstalk → tu entorno → **Acciones** → **Reiniciar servidores de aplicaciones**.

En 1–2 minutos el estado debe pasar a **Correcto (verde)**.

---

## Paso 5 · Verificar la API

Abre en el navegador (cambia por tu dominio):

- `http://veterinaria-api.us-east-1.elasticbeanstalk.com/api/health` → `{"ok":true,...}`
- `http://veterinaria-api.us-east-1.elasticbeanstalk.com/api/products` → los 6 productos
- `http://veterinaria-api.us-east-1.elasticbeanstalk.com/swagger-ui.html` → documentación. Prueba `POST /api/auth/login` con `admin@sanmarcos.cl / Admin123!`, copia el `accessToken`, presiona **Authorize** y llama a `GET /api/users`.

Si algo falla: Elastic Beanstalk → **Registros** → **Solicitar registros** → *Últimas 100 líneas* y busca el error en `web.stdout.log` (ver tabla de problemas al final).

---

## Paso 6 · Compilar el frontend apuntando a AWS

```bash
cd VETERINARIA_SAN_MARCOS
cp .env.production.example .env.production      # Windows: copy .env.production.example .env.production
```

Edita `.env.production`:

```text
VITE_API_URL=http://veterinaria-api.us-east-1.elasticbeanstalk.com/api
```

```bash
npm install
npm run build        # genera la carpeta dist/
```

> La URL queda "grabada" en el build. Si cambias la URL de la API, repite `npm run build` y vuelve a subir `dist/`.

---

## Paso 7 · S3 (frontend)

Consola → **S3** → **Crear bucket**:

1. Nombre: el elegido en el paso 0 (ej: `veterinaria-san-marcos-front-roang`), región `us-east-1`.
2. **Desmarca** "Bloquear todo el acceso público" y confirma el aviso. Crear.
3. Entra al bucket → **Cargar** → arrastra **el contenido** de `dist/` (el archivo `index.html` y la carpeta `assets/`, **no** la carpeta `dist` misma) → Cargar.
4. Pestaña **Propiedades** → *Alojamiento de sitios web estáticos* → **Editar** → Habilitar:
   - Documento de índice: `index.html`
   - Documento de error: `index.html` (necesario para que `/categorias`, `/admin`, etc. funcionen al recargar)
   - Guardar. Copia el **Punto de enlace del sitio web** (debe ser igual a la URL que pusiste en `APP_CORS_ORIGINS`).
5. Pestaña **Permisos** → *Política del bucket* → **Editar** → pega (cambia el nombre del bucket):
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Sid": "LecturaPublica",
         "Effect": "Allow",
         "Principal": "*",
         "Action": "s3:GetObject",
         "Resource": "arn:aws:s3:::veterinaria-san-marcos-front-roang/*"
       }
     ]
   }
   ```
   Guardar.

---

## Paso 8 · Prueba completa

Abre la URL del sitio S3 y recorre:

1. Inicio y **Categorías** cargan productos (vienen de RDS a través de la API).
2. **Registro** de un usuario nuevo → queda como CLIENTE.
3. Agregar al carrito → **Checkout** → compra exitosa → **Mis pedidos**.
4. Salir e ingresar con `admin@sanmarcos.cl / Admin123!` → **Administración**: crear/editar producto, cambiar estado de pedido (al **cancelar** vuelve el stock), cambiar rol de un usuario.
5. Con el cliente, entrar a `/admin` → debe mostrar **acceso restringido**.

---

## Actualizar después de cambios

- **Backend:** `./mvnw clean package` → Elastic Beanstalk → entorno → **Cargar e implementar** → `veterinaria.jar` con una etiqueta nueva (`v2`, `v3`...). Las migraciones nuevas van en `db/migration/V3__...sql` (nunca editar una V1/V2 ya aplicada).
- **Frontend:** `npm run build` → en S3 elimina el contenido anterior y sube el nuevo `dist/`.
- **Variables de entorno:** Elastic Beanstalk → **Configuración** → *Actualizaciones, monitoreo y registros* → Editar → Propiedades del entorno. Al guardar, la app se reinicia sola.

## Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| `502 Bad Gateway` en la API | La app no arrancó o no escucha en 5000 | Revisa `PORT=5000` y los registros (`web.stdout.log`) |
| Registro: `Communications link failure` | La API no llega a RDS | Regla de entrada 3306 del paso 4; endpoint correcto en `DB_URL` |
| Registro: `Access denied for user 'admin'` | Usuario/contraseña de RDS | Corrige `DB_USERNAME` / `DB_PASSWORD` |
| Registro: `Could not resolve placeholder 'JWT_SECRET'` o "al menos 32 caracteres" | Falta o es corto el secreto | Define `JWT_SECRET` con 32+ caracteres |
| En el navegador: error de **CORS** | `APP_CORS_ORIGINS` no coincide exactamente con la URL de S3 | Copia el punto de enlace de S3 tal cual, sin `/` final, y reinicia |
| Frontend: "No se pudo conectar con el servidor" | `VITE_API_URL` mal escrita o build antiguo | Revisa `.env.production`, `npm run build`, sube `dist/` de nuevo |
| Error de **contenido mixto** (mixed content) | Abriste el frontend por `https` y la API es `http` | Usa la URL `http://...s3-website...` (o pon HTTPS también en la API) |
| `403 Forbidden` al abrir el sitio S3 | Falta la política pública o el bloqueo sigue activo | Paso 7, puntos 2 y 5 |
| `404` al recargar `/categorias` | Falta el documento de error | Documento de error = `index.html` |
| Emojis de productos se ven como `?` | Base sin utf8mb4 | MySQL 8 usa utf8mb4 por defecto; si cambiaste el grupo de parámetros, vuelve a `utf8mb4` |

## Costos y limpieza

- RDS y la instancia de Beanstalk cobran por hora mientras existan (en AWS Academy se descuentan de tus créditos). Después de la evaluación:
  1. Elastic Beanstalk → entorno → **Acciones → Terminar entorno**.
  2. RDS → `veterinaria-db` → **Acciones → Eliminar** (o *Detener temporalmente*, máx. 7 días).
  3. S3 → vaciar y eliminar el bucket.
- Nunca subas contraseñas ni `JWT_SECRET` al repositorio: solo van en las propiedades del entorno de Elastic Beanstalk.

## Opcional: HTTPS

Para producción real se pondría **CloudFront** delante de S3 y un balanceador con certificado (ACM) en Beanstalk, ambos con HTTPS. Para la evaluación, HTTP en ambos lados es suficiente y evita el error de contenido mixto.

# Despliegue automático en EC2 con GitHub Actions

Cada push a `main` compila el backend (Spring Boot) y el frontend (React), y los instala en la EC2:

```
Navegador ──► http://IP-EC2  (Nginx :80)
                 ├── /            → frontend React (/var/www/veterinaria)
                 ├── /api/...     → backend Spring Boot (127.0.0.1:8080, servicio "veterinaria")
                 └── /swagger-ui  → documentación de la API
```

- Base de datos **H2 en memoria**: no hay persistencia. Cada despliegue o reinicio vuelve a los 6 productos y a los usuarios demo (`admin@sanmarcos.cl / Admin123!`, `cliente@sanmarcos.cl / Cliente123!`).
- En los Pull Requests solo se compila y se corren los tests; la EC2 no se toca.
- El workflow está en `.github/workflows/deploy-ec2.yml` y el instalador en `deploy/ec2-setup.sh`.

## 1. Preparar la EC2 (una sola vez)

1. **AMI:** Ubuntu 24.04 o 22.04 (también sirve Amazon Linux 2023). Basta con una `t2.micro` o `t3.micro`.
2. **Security Group → Inbound rules:**
   | Tipo | Puerto | Origen | Para qué |
   |---|---|---|---|
   | SSH | 22 | 0.0.0.0/0 | GitHub Actions entra por SSH (sus IPs cambian) |
   | HTTP | 80 | 0.0.0.0/0 | La página |
3. *(Recomendado)* Asocia una **Elastic IP** para que la IP no cambie al reiniciar la instancia.

No hay que instalar nada a mano: el workflow instala Java 21 y Nginx la primera vez.

## 2. Cargar los secretos en GitHub (una sola vez)

En el repo: **Settings → Secrets and variables → Actions → New repository secret**

| Nombre | Valor |
|---|---|
| `EC2_HOST` | IP pública (o DNS) de la instancia, ej. `3.89.120.45` |
| `EC2_SSH_KEY` | Todo el contenido del archivo `.pem`, desde `-----BEGIN` hasta `-----END ...-----` |
| `EC2_USER` | Solo si **no** es Ubuntu: `ec2-user` para Amazon Linux |

Para copiar el `.pem` en Windows (PowerShell): `Get-Content .\mi-llave.pem | Set-Clipboard`

## 3. Desplegar

- Haz merge a `main` (o push directo), **o**
- **Actions → Deploy EC2 → Run workflow**.

Al terminar, el resumen del workflow muestra la URL. Abre `http://EC2_HOST`.

## Revisar la EC2 (Termius / SSH)

```bash
sudo systemctl status veterinaria nginx     # estado de los servicios
sudo journalctl -u veterinaria -f           # logs del backend en vivo
curl http://127.0.0.1:8080/api/health       # backend directo
sudo systemctl restart veterinaria          # reiniciar (vuelve a los datos iniciales)
```

## Problemas comunes

| Síntoma en Actions | Causa probable |
|---|---|
| `Faltan los secretos EC2_HOST y/o EC2_SSH_KEY` | No se cargaron los secretos del paso 2 |
| `Connection timed out` en *Subir paquete* | El Security Group no permite el puerto 22, o la IP cambió |
| `Permission denied (publickey)` | `.pem` incompleto o `EC2_USER` incorrecto (`ubuntu` / `ec2-user`) |
| Falla *Comprobar desde internet* pero la EC2 dice "Listo" | El Security Group no permite el puerto 80 |
| `El backend no respondió` | Ver los logs que imprime el paso; o `sudo journalctl -u veterinaria -n 100` |

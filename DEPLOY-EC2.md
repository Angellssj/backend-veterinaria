# Despliegue automático en EC2 con GitHub Actions (runner self-hosted)

Cada push a `main` compila el backend (Spring Boot) y el frontend (React) en GitHub, y un **runner instalado dentro de la EC2** los despliega:

```
GitHub Actions ──(compila en ubuntu-latest)──► artefacto
       ▲
       │ el runner de la EC2 pregunta "¿hay trabajo?" (conexión saliente HTTPS)
       │
EC2 ── runner ─► instala jar + frontend ─► Nginx :80
                    ├── /            → frontend React (/var/www/veterinaria)
                    ├── /api/...     → backend Spring Boot (127.0.0.1:8080, servicio "veterinaria")
                    └── /swagger-ui  → documentación de la API
```

- **Sin SSH desde internet y sin llaves en GitHub**: la EC2 solo necesita el puerto 80 abierto.
- Base de datos **H2 en memoria**: no hay persistencia. Cada despliegue o reinicio vuelve a los 6 productos y a los usuarios demo (`admin@sanmarcos.cl / Admin123!`, `cliente@sanmarcos.cl / Cliente123!`).
- En los Pull Requests solo se compila y se corren los tests.

## 1. Instalar el runner en la EC2 (una sola vez)

1. En GitHub: **Settings → Actions → Runners → New self-hosted runner**, elige **Linux** y **x64**.
2. Abre una terminal en la EC2 (EC2 Instance Connect o Termius) y pega, en orden, los comandos de las secciones **Download** y **Configure** que muestra GitHub (traen tu token; no lo compartas).
   - Si `./config.sh` reclama librerías que faltan: `sudo ./bin/installdependencies.sh` y vuelve a ejecutarlo.
   - Cuando pregunte **"Enter any additional labels"**, escribe: `ec2`
   - En las demás preguntas, Enter (valores por defecto).
3. **No** ejecutes `./run.sh`. En su lugar, déjalo como servicio (arranca solo al reiniciar la instancia):
   ```bash
   sudo ./svc.sh install
   sudo ./svc.sh start
   ```
4. En **Settings → Actions → Runners** debe aparecer con estado **Idle** (verde) y las etiquetas `self-hosted`, `Linux`, `X64`, `ec2`.

## 2. Security Group (grupo `Backend`)

| Tipo | Puerto | Origen | Para qué |
|---|---|---|---|
| HTTP | 80 | `0.0.0.0/0` | La página |
| SSH | 22 | Lista de prefijos `com.amazonaws.us-east-1.ec2-instance-connect` | Seguir entrando con EC2 Instance Connect desde la consola |
| SSH | 22 | *Mi IP* (opcional) | Entrar con Termius desde tu PC |

Elimina la regla SSH `0.0.0.0/0`: el runner no la necesita.

## 3. Desplegar

- Push o merge a `main`, **o**
- **Actions → Deploy EC2 → Run workflow**.

Jobs: **Compilar y probar** (GitHub) → **Desplegar en EC2** (tu runner) → **Comprobar desde internet** (GitHub). El resumen muestra la URL.

## Seguridad del runner (repo público)

En **Settings → Actions → General → Approval for running fork pull request workflows from contributors**, elige **Require approval for all external contributors**. Así nadie de afuera puede hacer correr código en tu EC2 abriendo un PR.

## AWS Academy Learner Lab

- Al cerrarse el lab la instancia se detiene; al iniciarlo de nuevo, el runner y la app arrancan solos.
- La IP pública cambia en cada sesión (salvo que asocies una **IP elástica**). El workflow detecta la IP actual sola.

## Revisar la EC2

```bash
sudo systemctl status veterinaria nginx     # estado de la app y Nginx
sudo journalctl -u veterinaria -f           # logs del backend en vivo
cd ~/actions-runner && sudo ./svc.sh status # estado del runner
```

## Problemas comunes

| Síntoma | Causa probable |
|---|---|
| *Desplegar en EC2* queda en **Queued / Waiting for a runner** | Runner apagado o sin la etiqueta `ec2` (ver Settings → Actions → Runners) |
| `sudo: a password is required` | El runner se instaló con un usuario sin sudo; usar `ubuntu` o `ec2-user` |
| `El backend no respondió` | Ver los logs que imprime el paso; o `sudo journalctl -u veterinaria -n 100` |
| Falla *Comprobar desde internet* | El Security Group no permite el puerto 80 |

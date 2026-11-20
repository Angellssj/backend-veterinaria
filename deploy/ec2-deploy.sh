#!/usr/bin/env bash
# Despliega Veterinaria San Marcos (MySQL + Spring Boot + React/Nginx) en una EC2 Ubuntu
# usando Docker Compose.
#
# Estructura esperada (el script clona el frontend si falta):
#   ~/vetsm-app/backend   <- este repositorio (Angellssj/backend-veterinaria)
#   ~/vetsm-app/frontend  <- Roselin2831/VETERINARIA_SAN_MARCOS
#
# Uso:
#   bash backend/deploy/ec2-deploy.sh
#
# Es seguro ejecutarlo varias veces: trae los cambios de ambos repositorios (git pull),
# reconstruye los contenedores y conserva la base de datos.
#
# Variables opcionales:
#   FRONTEND_REPO    URL del repositorio frontend
#   FRONTEND_BRANCH  rama a clonar del frontend (por defecto, la principal)
#   SKIP_PULL=1      no ejecutar git pull
set -euo pipefail

FRONTEND_REPO="${FRONTEND_REPO:-https://github.com/Roselin2831/VETERINARIA_SAN_MARCOS.git}"
FRONTEND_BRANCH="${FRONTEND_BRANCH:-}"

log()  { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33mAVISO: %s\033[0m\n' "$*" >&2; }

# Todo va dentro de main(): bash lee el archivo completo antes de ejecutar, así
# "git pull" puede actualizar este mismo script sin romper la ejecución en curso.
main() {
  local BACKEND FRONTEND ENV_FILE HTTP_PORT OK TOKEN IP SUFFIX
  BACKEND="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  FRONTEND="$(dirname "$BACKEND")/frontend"
  ENV_FILE="$BACKEND/.env"

  [ -f "$BACKEND/docker-compose.aws.yml" ] || { echo "No encuentro $BACKEND/docker-compose.aws.yml" >&2; exit 1; }

  # 1. Paquetes: Docker, Compose y Git ---------------------------------------------
  if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1 \
     || ! command -v git >/dev/null 2>&1; then
    log "Instalando Docker, Docker Compose y Git"
    sudo apt-get update -y
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y docker.io docker-compose-v2 git openssl curl
    sudo systemctl enable --now docker
    sudo usermod -aG docker "$USER" || true
  fi
  local -a COMPOSE=(docker compose -f "$BACKEND/docker-compose.aws.yml" --project-directory "$BACKEND")
  # Si el grupo docker aún no está activo en esta sesión SSH, usar sudo.
  docker info >/dev/null 2>&1 || COMPOSE=(sudo "${COMPOSE[@]}")

  # 2. Código: clonar el frontend si falta y actualizar ambos repos ----------------
  if [ ! -d "$FRONTEND" ]; then
    log "Clonando el frontend en $FRONTEND"
    git clone ${FRONTEND_BRANCH:+--branch "$FRONTEND_BRANCH"} "$FRONTEND_REPO" "$FRONTEND"
  elif [ "${SKIP_PULL:-0}" != 1 ] && [ -d "$FRONTEND/.git" ]; then
    log "Actualizando frontend (git pull)"
    git -C "$FRONTEND" pull --ff-only || warn "No se pudo actualizar el frontend; se usa el código actual."
  fi
  if [ "${SKIP_PULL:-0}" != 1 ] && [ -d "$BACKEND/.git" ]; then
    log "Actualizando backend (git pull)"
    git -C "$BACKEND" pull --ff-only || warn "No se pudo actualizar el backend; se usa el código actual."
  fi
  [ -f "$FRONTEND/Dockerfile" ] || { echo "No encuentro $FRONTEND/Dockerfile" >&2; exit 1; }
  grep -q 'location /api/' "$FRONTEND/nginx.conf" 2>/dev/null \
    || warn "frontend/nginx.conf no reenvía /api al backend: el sitio no podrá llamar a la API. Usa la versión del frontend con proxy /api."

  # 3. Liberar el puerto 80 si quedó el despliegue antiguo sin Docker --------------
  local svc
  for svc in nginx veterinaria; do
    if systemctl is-active --quiet "$svc" 2>/dev/null; then
      log "Deteniendo el servicio '$svc' instalado fuera de Docker (despliegue anterior)"
      sudo systemctl disable --now "$svc"
    fi
  done

  # 4. Swap: compilar Java + Node con 1 GiB de RAM suele fallar sin ella -----------
  if [ "$(swapon --show --noheadings | wc -l)" -eq 0 ]; then
    log "Creando 2 GiB de swap (/swapfile)"
    sudo fallocate -l 2G /swapfile || sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
  fi

  # 5. .env con secretos aleatorios (solo si no existe) ---------------------------
  if [ ! -f "$ENV_FILE" ]; then
    log "Generando .env con secretos aleatorios"
    local ADMIN_PASS="Adm-$(openssl rand -hex 8)"
    cat > "$ENV_FILE" <<EOF
HTTP_PORT=80
SPRING_PORT=8080
SPRING_BIND=127.0.0.1
MYSQL_PASSWORD=$(openssl rand -hex 24)
MYSQL_ROOT_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET=$(openssl rand -hex 48)
APP_ADMIN_EMAIL=admin@sanmarcos.cl
APP_ADMIN_PASSWORD=$ADMIN_PASS
APP_SEED_DEMO_CLIENT=false
CORS_ALLOWED_ORIGINS=
EOF
    chmod 600 "$ENV_FILE"
    echo "Usuario administrador:    admin@sanmarcos.cl"
    echo "Contraseña administrador: $ADMIN_PASS   (queda guardada en $ENV_FILE)"
  fi

  # 6. Construir y levantar ---------------------------------------------------------
  log "Construyendo imágenes y levantando contenedores (la primera vez tarda varios minutos)"
  "${COMPOSE[@]}" up --build -d --remove-orphans

  # 7. Esperar a que la API responda a través de Nginx -----------------------------
  HTTP_PORT="$(grep -E '^HTTP_PORT=' "$ENV_FILE" | cut -d= -f2)"; HTTP_PORT="${HTTP_PORT:-80}"
  log "Esperando a que la API responda en http://localhost:$HTTP_PORT/api/health"
  OK=0
  for _ in $(seq 1 90); do
    if curl -fsS "http://localhost:$HTTP_PORT/api/health" >/dev/null 2>&1; then OK=1; break; fi
    sleep 2
  done
  if [ "$OK" != 1 ]; then
    echo "La API no respondió a tiempo. Estado y logs:" >&2
    "${COMPOSE[@]}" ps >&2
    "${COMPOSE[@]}" logs --tail=60 backend frontend >&2
    exit 1
  fi

  # 8. URL pública (servicio de metadatos IMDSv2) ---------------------------------
  TOKEN="$(curl -fsS -m 3 -X PUT http://169.254.169.254/latest/api/token \
            -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' 2>/dev/null || true)"
  IP="$(curl -fsS -m 3 -H "X-aws-ec2-metadata-token: $TOKEN" \
            http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || echo '<IP_PUBLICA_EC2>')"
  SUFFIX=""; [ "$HTTP_PORT" != 80 ] && SUFFIX=":$HTTP_PORT"

  "${COMPOSE[@]}" ps
  log "Listo"
  echo "Sitio:   http://$IP$SUFFIX/"
  echo "API:     http://$IP$SUFFIX/api/health"
  echo "Swagger: http://$IP$SUFFIX/swagger-ui.html"
  echo "Recuerda permitir el puerto $HTTP_PORT (HTTP) en el Security Group de la instancia."
}

main "$@"

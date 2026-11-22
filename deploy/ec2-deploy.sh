#!/usr/bin/env bash
# Despliega Veterinaria San Marcos (MySQL + Spring Boot + React/Nginx) en una EC2 Ubuntu.
#
# Uso, desde la carpeta raiz del proyecto (la que contiene backend/ y frontend/):
#   bash deploy/ec2-deploy.sh
#
# Es seguro ejecutarlo varias veces: actualiza los contenedores y conserva la base de datos.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND="$ROOT/backend"
COMPOSE=(docker compose -f "$BACKEND/docker-compose.aws.yml" --project-directory "$BACKEND")

log() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }

[ -f "$BACKEND/docker-compose.aws.yml" ] && [ -f "$ROOT/frontend/Dockerfile" ] || {
  echo "No encuentro backend/docker-compose.aws.yml o frontend/Dockerfile en $ROOT" >&2; exit 1; }

# 1. Docker -----------------------------------------------------------------------
if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  log "Instalando Docker y Docker Compose"
  sudo apt-get update -y
  sudo apt-get install -y docker.io docker-compose-v2 openssl curl
  sudo systemctl enable --now docker
  sudo usermod -aG docker "$USER" || true
fi
# Si el usuario aun no tiene el grupo docker activo en esta sesion, usar sudo.
if ! docker info >/dev/null 2>&1; then
  COMPOSE=(sudo "${COMPOSE[@]}")
fi

# 2. Swap: compilar Java + Node en 1 GiB de RAM suele fallar sin ella ---------------
if [ "$(swapon --show --noheadings | wc -l)" -eq 0 ]; then
  log "Creando 2 GiB de swap (/swapfile)"
  sudo fallocate -l 2G /swapfile || sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

# 3. .env con secretos aleatorios (solo si no existe) ------------------------------
ENV_FILE="$BACKEND/.env"
if [ ! -f "$ENV_FILE" ]; then
  log "Generando backend/.env con secretos aleatorios"
  ADMIN_PASS="Adm-$(openssl rand -hex 8)"
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
  echo "Usuario administrador: admin@sanmarcos.cl"
  echo "Contrasena administrador: $ADMIN_PASS   (queda guardada en backend/.env)"
fi

# 4. Construir y levantar ----------------------------------------------------------
log "Construyendo imagenes y levantando contenedores (la primera vez tarda varios minutos)"
"${COMPOSE[@]}" up --build -d --remove-orphans

# 5. Esperar a que la API responda a traves de Nginx ------------------------------
HTTP_PORT="$(grep -E '^HTTP_PORT=' "$ENV_FILE" | cut -d= -f2)"; HTTP_PORT="${HTTP_PORT:-80}"
log "Esperando a que la API responda en http://localhost:$HTTP_PORT/api/health"
for _ in $(seq 1 90); do
  if curl -fsS "http://localhost:$HTTP_PORT/api/health" >/dev/null 2>&1; then OK=1; break; fi
  sleep 2
done
if [ "${OK:-0}" != 1 ]; then
  echo "La API no respondio a tiempo. Revisa los logs:" >&2
  "${COMPOSE[@]}" ps >&2
  "${COMPOSE[@]}" logs --tail=60 backend >&2
  exit 1
fi

# 6. URL publica (IMDSv2) -----------------------------------------------------------
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
echo "Recuerda abrir el puerto $HTTP_PORT (HTTP) en el Security Group de la instancia."

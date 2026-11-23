#!/usr/bin/env bash
# Ejecutar desde el repositorio backend: bash deploy/ec2-deploy.sh
set -Eeuo pipefail
umask 077
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND="$ROOT/backend"
ENV_FILE="$BACKEND/.env"
SUDO=()
if [ "$(id -u)" -ne 0 ]; then SUDO=(sudo); fi
DOCKER=(docker)
log() { printf '\n==> %s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
env_value() { sed -n "s/^${1}=//p" "$ENV_FILE" | tail -n 1 | tr -d '\r'; }

[ -f "$BACKEND/docker-compose.aws.yml" ] || fail 'No se encuentra backend/docker-compose.aws.yml.'
[ -f /etc/os-release ] || fail 'Se requiere Ubuntu, Debian o Amazon Linux.'
. /etc/os-release

# Dependencias del servidor. No se ejecutan scripts remotos de instalación.
case "$ID" in
  ubuntu|debian)
    "${SUDO[@]}" apt-get update -y
    "${SUDO[@]}" apt-get install -y ca-certificates curl openssl jq git gzip
    if ! command -v docker >/dev/null 2>&1; then
      "${SUDO[@]}" install -m 0755 -d /etc/apt/keyrings
      "${SUDO[@]}" curl -fsSL "https://download.docker.com/linux/$ID/gpg" -o /etc/apt/keyrings/docker.asc
      "${SUDO[@]}" chmod a+r /etc/apt/keyrings/docker.asc
      printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/%s %s stable\n' \
        "$(dpkg --print-architecture)" "$ID" "${UBUNTU_CODENAME:-$VERSION_CODENAME}" \
        | "${SUDO[@]}" tee /etc/apt/sources.list.d/docker-vetsm.list >/dev/null
      "${SUDO[@]}" apt-get update -y
      "${SUDO[@]}" apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    fi
    ;;
  amzn)
    "${SUDO[@]}" yum install -y openssl jq git gzip
    # AL2023 ya incluye curl-minimal; no sustituirlo por el paquete curl completo.
    if ! command -v curl >/dev/null 2>&1; then "${SUDO[@]}" yum install -y curl; fi
    if ! command -v docker >/dev/null 2>&1; then
      if [ "${VERSION_ID:-}" = 2 ] && command -v amazon-linux-extras >/dev/null 2>&1; then
        "${SUDO[@]}" amazon-linux-extras install docker -y
      else
        "${SUDO[@]}" yum install -y docker
      fi
    fi
    ;;
  *) fail "Sistema no soportado automáticamente: $ID. Instala Docker y Compose antes de continuar." ;;
esac
"${SUDO[@]}" systemctl enable --now docker

# Compose para instalaciones que incluyen Docker pero no su plugin (por ejemplo AL2023).
if ! docker compose version >/dev/null 2>&1; then
  architecture="$(uname -m)"
  case "$architecture" in x86_64|aarch64) ;; *) fail "Arquitectura no soportada: $architecture" ;; esac
  compose_version="${COMPOSE_VERSION:-v5.6.0}"
  "${SUDO[@]}" install -d /usr/local/lib/docker/cli-plugins
  plugin="$(mktemp)"
  checksums="$(mktemp)"
  curl -fsSL "https://github.com/docker/compose/releases/download/$compose_version/docker-compose-linux-$architecture" -o "$plugin"
  curl -fsSL "https://github.com/docker/compose/releases/download/$compose_version/checksums.txt" -o "$checksums"
  expected="$(awk -v name="docker-compose-linux-$architecture" '$2 == name || $2 == "*"name {print $1}' "$checksums")"
  [ -n "$expected" ] || fail 'No se encontró el checksum oficial de Docker Compose.'
  printf '%s  %s\n' "$expected" "$plugin" | sha256sum --check --status || fail 'Checksum de Compose incorrecto.'
  "${SUDO[@]}" install -m 0755 "$plugin" /usr/local/lib/docker/cli-plugins/docker-compose
  rm -f -- "$plugin" "$checksums"
fi
if ! docker buildx version >/dev/null 2>&1; then
  case "$(uname -m)" in x86_64) build_arch=amd64 ;; aarch64) build_arch=arm64 ;; *) fail 'Arquitectura no soportada para Buildx.' ;; esac
  build_version="${BUILDX_VERSION:-v0.39.1}"
  build_asset="buildx-$build_version.linux-$build_arch"
  plugin="$(mktemp)"; checksums="$(mktemp)"
  "${SUDO[@]}" install -d /usr/local/lib/docker/cli-plugins
  curl -fsSL "https://github.com/docker/buildx/releases/download/$build_version/$build_asset" -o "$plugin"
  curl -fsSL "https://github.com/docker/buildx/releases/download/$build_version/checksums.txt" -o "$checksums"
  expected="$(awk -v name="$build_asset" '$2 == name || $2 == "*"name {print $1}' "$checksums")"
  [ -n "$expected" ] || fail 'No se encontró el checksum oficial de Buildx.'
  printf '%s  %s\n' "$expected" "$plugin" | sha256sum --check --status || fail 'Checksum de Buildx incorrecto.'
  "${SUDO[@]}" install -m 0755 "$plugin" /usr/local/lib/docker/cli-plugins/docker-buildx
  rm -f -- "$plugin" "$checksums"
fi
if ! docker info >/dev/null 2>&1; then DOCKER=("${SUDO[@]}" docker); fi
"${DOCKER[@]}" info >/dev/null
COMPOSE=("${DOCKER[@]}" compose -f "$BACKEND/docker-compose.aws.yml" --project-directory "$BACKEND")
"${DOCKER[@]}" compose version

# FRONTEND_DIR permite construir directamente el segundo repositorio clonado.
if [ -n "${FRONTEND_DIR:-}" ]; then
  FRONTEND_DIR="$(cd "$FRONTEND_DIR" && pwd)"
  [ -f "$FRONTEND_DIR/Dockerfile" ] || fail 'FRONTEND_DIR no contiene un Dockerfile.'
  export FRONTEND_DIR
else
  [ -f "$ROOT/frontend/Dockerfile" ] || fail 'Falta frontend/Dockerfile o FRONTEND_DIR.'
fi

# No cambiar los secretos de un volumen existente al perder accidentalmente el .env.
if [ ! -f "$ENV_FILE" ]; then
  if "${DOCKER[@]}" volume inspect backend_mysql_data >/dev/null 2>&1; then
    fail 'Ya existe backend_mysql_data. Recupera el backend/.env original para conservar sus credenciales.'
  fi
  log 'Generando backend/.env con credenciales aleatorias'
  cat > "$ENV_FILE" <<EOF
HTTP_PORT=80
SPRING_PORT=8080
SPRING_BIND=127.0.0.1
MYSQL_PASSWORD=$(openssl rand -hex 24)
MYSQL_ROOT_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET=$(openssl rand -hex 48)
APP_ADMIN_EMAIL=admin@sanmarcos.cl
APP_ADMIN_PASSWORD=Adm-$(openssl rand -hex 12)
APP_SEED_DEMO_CLIENT=false
CORS_ALLOWED_ORIGINS=
VITE_API_URL=/api/v1
EOF
fi
chmod 600 "$ENV_FILE"
for key in MYSQL_PASSWORD MYSQL_ROOT_PASSWORD JWT_SECRET APP_ADMIN_EMAIL APP_ADMIN_PASSWORD; do
  [ -n "$(env_value "$key")" ] || fail "Falta $key en backend/.env. Completa ese archivo antes de continuar."
done

# En máquinas pequeñas, crear swap propia sin sobrescribir un archivo existente.
memory_kb="$(awk '/MemTotal:/ {print $2}' /proc/meminfo)"
if [ "${ENABLE_SWAP:-auto}" != false ] && [ "$memory_kb" -lt 2000000 ] && [ "$(swapon --show --noheadings | wc -l)" -eq 0 ]; then
  swap_file=/swapfile-vetsm
  [ ! -e "$swap_file" ] || fail "$swap_file ya existe. Revísalo o ejecuta con ENABLE_SWAP=false."
  available_kb="$(df -Pk / | awk 'NR==2 {print $4}')"
  [ "$available_kb" -gt 3145728 ] || fail 'Se requieren al menos 3 GiB libres para crear swap y compilar.'
  log 'Creando 2 GiB de swap para la compilación'
  "${SUDO[@]}" dd if=/dev/zero of="$swap_file" bs=1M count=2048 status=none
  "${SUDO[@]}" chmod 600 "$swap_file"
  "${SUDO[@]}" mkswap "$swap_file" >/dev/null
  "${SUDO[@]}" swapon "$swap_file"
  printf '%s none swap sw 0 0\n' "$swap_file" | "${SUDO[@]}" tee -a /etc/fstab >/dev/null
fi

if "${COMPOSE[@]}" ps --status running --services 2>/dev/null | grep -qx mysql; then
  log 'Guardando respaldo MySQL antes de actualizar'
  bash "$ROOT/deploy/backup-mysql.sh"
fi
log 'Construyendo imágenes por separado para reducir el uso máximo de memoria'
"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" build backend
"${COMPOSE[@]}" build frontend
log 'Iniciando MySQL, backend y frontend'
if ! "${COMPOSE[@]}" up -d --remove-orphans --wait --wait-timeout 300; then
  "${COMPOSE[@]}" ps >&2
  "${COMPOSE[@]}" logs --tail=80 backend mysql >&2
  fail 'El stack no quedó saludable. Los volúmenes y el .env se conservaron.'
fi
bash "$ROOT/deploy/verificar.sh"

http_port="$(env_value HTTP_PORT)"; http_port="${http_port:-80}"
imds_token="$(curl -fsS --max-time 3 -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' 2>/dev/null || true)"
public_host="${PUBLIC_HOST:-$(curl -fsS --max-time 3 -H "X-aws-ec2-metadata-token: $imds_token" http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || true)}"
public_host="${public_host:-IP_PUBLICA_EC2}"
suffix=''; if [ "$http_port" != 80 ]; then suffix=":$http_port"; fi
"${COMPOSE[@]}" ps
log 'Despliegue verificado localmente en el servidor'
printf 'Frontend: http://%s%s/\nAPI: http://%s%s/api/v1/products\nSwagger: http://%s%s/swagger-ui.html\n' "$public_host" "$suffix" "$public_host" "$suffix" "$public_host" "$suffix"
printf 'Administrador: %s\nContraseña: consulta APP_ADMIN_PASSWORD en %s\n' "$(env_value APP_ADMIN_EMAIL)" "$ENV_FILE"
printf 'Abre TCP %s en el Security Group para que el sitio sea accesible desde internet.\n' "$http_port"

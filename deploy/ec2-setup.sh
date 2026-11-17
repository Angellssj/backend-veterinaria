#!/usr/bin/env bash
# Se ejecuta EN la EC2 (lo llama GitHub Actions por SSH con sudo). Se puede correr las veces que sea.
#   sudo bash ec2-setup.sh <carpeta-con-paquete> <ip-o-dominio-publico>
# La carpeta debe traer: veterinaria.jar, frontend.tgz
#
# Deja:
#   - Backend como servicio systemd "veterinaria" en 127.0.0.1:8080 (H2 en memoria: sin persistencia)
#   - Frontend en /var/www/veterinaria servido por Nginx en el puerto 80
#   - Nginx reenvía /api, /swagger-ui y /v3/api-docs al backend
# Funciona en Ubuntu 22.04/24.04 y Amazon Linux 2023.
set -euo pipefail

SRC="${1:-/tmp/veterinaria-deploy}"
PUBLIC_HOST="${2:-}"
APP_DIR=/opt/veterinaria
WEB_DIR=/var/www/veterinaria
SERVICE=veterinaria

log()  { echo "==> $*"; }
fail() { echo "ERROR: $*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || fail "Ejecutar con sudo."
[ -f "$SRC/veterinaria.jar" ] || fail "No encuentro $SRC/veterinaria.jar"
[ -f "$SRC/frontend.tgz" ]    || fail "No encuentro $SRC/frontend.tgz"

# Busca un Java 21 o superior instalado
find_java() {
  local j v
  for j in /usr/lib/jvm/*/bin/java $(command -v java 2>/dev/null || true); do
    [ -x "$j" ] || continue
    v=$("$j" -version 2>&1 | awk -F'"' '/version/ {print $2; exit}' | cut -d. -f1)
    if [ -n "$v" ] && [ "$v" -ge 21 ] 2>/dev/null; then echo "$j"; return 0; fi
  done
  return 1
}

# ---------- 1. Swap (instancias de 1 GB como t2.micro / t3.micro) ----------
RAM_MB=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
if [ "$RAM_MB" -lt 2000 ] && [ -z "$(swapon --show 2>/dev/null)" ]; then
  log "Creando swap de 2 GB (RAM: ${RAM_MB} MB)"
  fallocate -l 2G /swapfile 2>/dev/null || dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# ---------- 2. Paquetes: Java 21 y Nginx ----------
if command -v apt-get >/dev/null; then
  export DEBIAN_FRONTEND=noninteractive
  pkgs=()
  find_java >/dev/null || pkgs+=(openjdk-21-jre-headless)
  command -v nginx >/dev/null || pkgs+=(nginx)
  command -v curl  >/dev/null || pkgs+=(curl)
  if [ ${#pkgs[@]} -gt 0 ]; then
    log "Instalando: ${pkgs[*]}"
    apt-get -o DPkg::Lock::Timeout=300 update -y -qq
    apt-get -o DPkg::Lock::Timeout=300 install -y -qq "${pkgs[@]}"
  fi
  NGINX_CONF=/etc/nginx/sites-available/veterinaria
elif command -v dnf >/dev/null; then
  pkgs=()
  find_java >/dev/null || pkgs+=(java-21-amazon-corretto-headless)
  command -v nginx >/dev/null || pkgs+=(nginx)
  if [ ${#pkgs[@]} -gt 0 ]; then
    log "Instalando: ${pkgs[*]}"
    dnf install -y -q "${pkgs[@]}"
  fi
  NGINX_CONF=/etc/nginx/conf.d/veterinaria.conf
else
  fail "Sistema no soportado (se necesita apt o dnf)."
fi
JAVA_BIN=$(find_java) || fail "No se pudo instalar Java 21."
log "Java: $JAVA_BIN"

# ---------- 3. Backend ----------
log "Instalando backend en $APP_DIR"
id -u veterinaria >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin veterinaria
install -d -m 750 -o veterinaria -g veterinaria "$APP_DIR"
install -m 640 -o veterinaria -g veterinaria "$SRC/veterinaria.jar" "$APP_DIR/app.jar"

# Secreto JWT: se genera una sola vez y se conserva entre despliegues
if [ ! -s "$APP_DIR/jwt.secret" ]; then
  head -c 48 /dev/urandom | od -An -tx1 | tr -d ' \n' > "$APP_DIR/jwt.secret"
fi
chmod 600 "$APP_DIR/jwt.secret"

ORIGINS="http://localhost:5173"
[ -n "$PUBLIC_HOST" ] && ORIGINS="http://$PUBLIC_HOST,https://$PUBLIC_HOST,$ORIGINS"

# Sin perfil "prod": usa H2 en memoria (los datos se reinician en cada despliegue/reinicio).
cat > "$APP_DIR/app.env" <<EOF
PORT=8080
SERVER_ADDRESS=127.0.0.1
SERVER_FORWARD_HEADERS_STRATEGY=framework
JWT_SECRET=$(cat "$APP_DIR/jwt.secret")
APP_CORS_ORIGINS=$ORIGINS
SPRING_H2_CONSOLE_ENABLED=false
EOF
chown root:veterinaria "$APP_DIR/app.env" && chmod 640 "$APP_DIR/app.env"

cat > /etc/systemd/system/$SERVICE.service <<EOF
[Unit]
Description=Veterinaria San Marcos - API Spring Boot
After=network.target

[Service]
User=veterinaria
WorkingDirectory=$APP_DIR
EnvironmentFile=$APP_DIR/app.env
ExecStart=$JAVA_BIN -Xms128m -Xmx512m -jar $APP_DIR/app.jar
SuccessExitStatus=143
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

# ---------- 4. Frontend ----------
log "Publicando frontend en $WEB_DIR"
rm -rf "$WEB_DIR" && mkdir -p "$WEB_DIR"
tar -xzf "$SRC/frontend.tgz" -C "$WEB_DIR"
chmod -R a+rX "$WEB_DIR"
[ -f "$WEB_DIR/index.html" ] || fail "El frontend no trae index.html"

# ---------- 5. Nginx ----------
log "Configurando Nginx"
cat > "$NGINX_CONF" <<'EOF'
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;

    root /var/www/veterinaria;
    index index.html;
    client_max_body_size 2m;

    # API Spring Boot
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }

    # Documentación Swagger
    location ~ ^/(swagger-ui|v3/api-docs) {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Archivos generados por Vite (con hash en el nombre)
    location /assets/ {
        try_files $uri =404;
        expires 7d;
        add_header Cache-Control "public";
    }

    # React Router: cualquier otra ruta (/categorias, /admin...) carga index.html
    location / {
        try_files $uri $uri/ /index.html;
    }
}
EOF

if [ -d /etc/nginx/sites-enabled ]; then
  ln -sf "$NGINX_CONF" /etc/nginx/sites-enabled/veterinaria
  rm -f /etc/nginx/sites-enabled/default
else
  # Amazon Linux: el nginx.conf trae su propio server en el puerto 80; el nuestro queda como default
  sed -i 's/ default_server//g' /etc/nginx/nginx.conf
fi

# SELinux (si está activo): permitir que Nginx lea /var/www y se conecte al backend
if command -v setsebool >/dev/null && [ "$(getenforce 2>/dev/null)" = "Enforcing" ]; then
  setsebool -P httpd_can_network_connect 1 || true
  command -v restorecon >/dev/null && restorecon -R "$WEB_DIR" || true
fi

# Firewall local (si ufw está activo). El Security Group de AWS se abre en la consola.
if command -v ufw >/dev/null && ufw status 2>/dev/null | grep -q "Status: active"; then
  ufw allow 80/tcp >/dev/null
fi

nginx -t
systemctl enable nginx >/dev/null 2>&1
systemctl restart nginx

# ---------- 6. Arrancar backend y verificar ----------
log "Reiniciando backend"
systemctl daemon-reload
systemctl enable $SERVICE >/dev/null 2>&1
systemctl restart $SERVICE

for _ in $(seq 1 60); do
  curl -fsS http://127.0.0.1:8080/api/health >/dev/null 2>&1 && break
  sleep 2
done
if ! curl -fsS http://127.0.0.1:8080/api/health >/dev/null 2>&1; then
  journalctl -u $SERVICE -n 80 --no-pager || true
  fail "El backend no respondió en 2 minutos (logs arriba)."
fi
curl -fsS http://127.0.0.1/api/products >/dev/null || fail "Nginx no está reenviando /api al backend."

log "Listo: http://${PUBLIC_HOST:-<ip-publica>}  ·  logs: sudo journalctl -u $SERVICE -f"

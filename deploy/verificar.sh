#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
settings="$(docker compose -f "$ROOT/backend/docker-compose.aws.yml" --project-directory "$ROOT/backend" config --format json)"
port="$(jq -er '.services.frontend.ports[0].published' <<< "$settings")"
email="$(jq -er '.services.backend.environment.APP_ADMIN_EMAIL' <<< "$settings")"
password="$(jq -er '.services.backend.environment.APP_ADMIN_PASSWORD' <<< "$settings")"
base="http://127.0.0.1:$port"
curl -fsS --max-time 20 "$base/" | grep -q '<div id="root">'
curl -fsS --max-time 20 "$base/categorias" | grep -q '<div id="root">'
curl -fsS --max-time 20 "$base/api/v1/health" | jq -e '.ok == true' >/dev/null
curl -fsS --max-time 20 "$base/api/v1/products" | jq -e 'type == "array" and length > 0' >/dev/null
curl -fsS --max-time 20 "$base/v3/api-docs" | jq -e '.paths["/api/v1/products"] != null' >/dev/null
curl -fsSL --max-time 20 "$base/swagger-ui.html" >/dev/null
response="$(mktemp)"; chmod 600 "$response"
trap 'rm -f -- "$response"' EXIT
payload="$(jq -n --arg email "$email" --arg password "$password" '{email:$email,password:$password}')"
status="$(curl -sS --max-time 20 -o "$response" -w '%{http_code}' "$base/api/v1/auth/login" -H "Origin: $base" -H 'Content-Type: application/json' --data "$payload")"
[ "$status" = 200 ] || { echo "Login falló con HTTP $status. Revisa APP_ADMIN_EMAIL y APP_ADMIN_PASSWORD." >&2; exit 1; }
token="$(jq -er '.accessToken' "$response")"
curl -fsS --max-time 20 "$base/api/v1/users" -H "Authorization: Bearer $token" | jq -e 'type == "array"' >/dev/null
curl -fsS --max-time 20 -X POST "$base/api/v1/auth/logout" -H "Authorization: Bearer $token" >/dev/null
printf 'OK: frontend, rutas React, API, consulta MySQL, Swagger, login, rol ADMIN y logout.\n'

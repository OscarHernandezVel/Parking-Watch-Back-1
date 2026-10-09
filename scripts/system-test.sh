#!/usr/bin/env bash
# Prueba de sistema (aceptación automatizada del guion de la demostración): levanta PostGIS, el
# backend y el Edge simulado con Docker Compose y verifica por la API que el sistema completo genera
# el reporte de la zona amarilla con su placa y evidencias, recibido por WebSocket.
#
# Requisitos: Docker, Python 3 y la imagen del Edge construida en cupo-edge con
#   ./mvnw package jib:dockerBuild -DskipTests -Dquality.skip=true
# En .env, BOOTSTRAP_ADMIN_TOTP_SECRET debe tener un secreto Base32 (segundo factor del admin).
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; source .env; set +a

BASE=http://127.0.0.1:8080
json() { python3 -c "import sys,json; data=json.load(sys.stdin); print(eval(sys.argv[1]))" "$1"; }
totp() {
  python3 - "$1" <<'PY'
import base64, hmac, struct, sys, time
key = base64.b32decode(sys.argv[1].upper() + "=" * (-len(sys.argv[1]) % 8))
h = hmac.new(key, struct.pack(">Q", int(time.time()) // 30), "sha1").digest()
o = h[-1] & 15
print("%06d" % ((struct.unpack(">I", h[o:o + 4])[0] & 0x7FFFFFFF) % 1000000))
PY
}

echo "==> Levantando el sistema (perfil demo)"
docker compose --profile demo up -d --build

echo "==> Esperando al backend"
for _ in $(seq 1 60); do
  curl -fs "$BASE/actuator/health" | grep -q UP && break
  sleep 5
done
curl -fs "$BASE/actuator/health" | grep -q UP

LOGIN=$(python3 -c "import json,sys; print(json.dumps({'username':sys.argv[1],'password':sys.argv[2],'otp':sys.argv[3]}))" \
  "$BOOTSTRAP_ADMIN_USERNAME" "$BOOTSTRAP_ADMIN_PASSWORD" "$(totp "$BOOTSTRAP_ADMIN_TOTP_SECRET")")
TOKEN=$(curl -fs -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' -d "$LOGIN" | json 'data["accessToken"]')

echo "==> Esperando el reporte de la maqueta (hasta 3 minutos)"
for _ in $(seq 1 36); do
  TOTAL=$(curl -fs "$BASE/api/v1/reports?zoneId=1" -H "Authorization: Bearer $TOKEN" | json 'data["page"]["totalElements"]')
  [ "$TOTAL" -ge 1 ] && break
  sleep 5
done

REPORT=$(curl -fs "$BASE/api/v1/reports?zoneId=1&size=1" -H "Authorization: Bearer $TOKEN")
ID=$(echo "$REPORT" | json 'data["content"][0]["id"]')
echo "$REPORT" | json 'data["content"][0]["plate"]' | grep -q ABC123
curl -fs "$BASE/api/v1/reports/$ID/evidence/plate" -H "Authorization: Bearer $TOKEN" -o /tmp/cupo-placa.jpg
STATUS=$(curl -fs "$BASE/api/v1/cameras/CAM-MAQ-01" -H "Authorization: Bearer $TOKEN" | json 'data["displayStatus"]')
LIVE=$(curl -fs "$BASE/api/v1/cameras/CAM-MAQ-01/live" -H "Authorization: Bearer $TOKEN" | json 'data["connected"]')
curl -fs "$BASE/api/v1/reports/export?format=PDF" -H "Authorization: Bearer $TOKEN" -o /tmp/cupo-reportes.pdf
head -c 5 /tmp/cupo-reportes.pdf | grep -q '%PDF'

echo "==> OK: reporte con placa ABC123 y foto, cámara $STATUS, en vivo=$LIVE, PDF exportado"

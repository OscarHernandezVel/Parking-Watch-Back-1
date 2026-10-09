# cupo-backend

Backend del sistema de reconocimiento de parqueo en zonas no autorizadas (Módulo 2 del
documento v6): Java 21 con Spring Boot 3, desplegable en **Coolify** con Docker, PostgreSQL +
PostGIS y disco persistente en `/data`.

| Módulo | Artefacto | Contenido |
|---|---|---|
| `contracts` | `com.parkingwatch:cupo-contracts` (GitHub Packages) | Mensajes, rutas HTTPS (`EdgeApi`), destinos STOMP (`EdgeStomp`) y ejemplos JSON que usa el Edge. |
| `app` | `cupo-backend.jar` / imagen Docker | API REST, WebSocket STOMP, reglas, reportes, exportaciones, modelos y migraciones Flyway. |

Contratos publicados: [`docs/openapi.json`](docs/openapi.json) (generado por la prueba
`AccessAndZonesIT`; la CI falla si no está al día) y [`docs/asyncapi.yaml`](docs/asyncapi.yaml).

## Desarrollo

```bash
cp .env.example .env                     # secretos locales
docker compose up -d                     # PostGIS (puerto 5433)
./mvnw verify                            # formato, Checkstyle, PMD, SpotBugs, pruebas, cobertura >= 80 %
./mvnw install -DskipTests               # instala cupo-contracts para compilar cupo-edge
docker compose --profile app up -d --build   # backend en http://localhost:8080 (/swagger-ui.html)
bash scripts/system-test.sh              # prueba de sistema con el Edge simulado (perfil demo)
```

Las pruebas de integración usan Testcontainers (Docker debe estar en ejecución) y prueban el
WebSocket con un servidor real (`RealtimeIT`).

## Variables de entorno (producción)

| Variable | Uso |
|---|---|
| `DATABASE_URL` | URL JDBC del PostgreSQL/PostGIS en la red privada, por ejemplo `jdbc:postgresql://postgis:5432/cupo`. |
| `JWT_SECRET`, `JWT_TTL` | Firma y duración del JWT (30 min por defecto). |
| `CORS_ALLOWED_ORIGIN` | Origen HTTPS exacto del frontend (también origen permitido del WebSocket `/ws`). |
| `EDGE_DEVICE_TOKENS` | `CAM-MAQ-01=pwd_...,CAM-02=pwd_...`; solo se guarda su SHA-256. |
| `STORAGE_PATH` | Disco persistente (`/data`). |
| `ADMIN_MFA_REQUIRED`, `LOGIN_MAX_ATTEMPTS`, `LOGIN_LOCK_DURATION` | Segundo factor y límite de intentos. |
| `BOOTSTRAP_*` | Datos de la maqueta y administrador inicial (y su secreto TOTP opcional). |

Despliegue: [`Dockerfile`](Dockerfile); ver [`docs/DESPLIEGUE.md`](docs/DESPLIEGUE.md).
El archivo [`render.yaml`](render.yaml) conserva la configuración anterior de Render.

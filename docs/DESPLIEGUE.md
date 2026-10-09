# Despliegue autohospedado de Parking Watch con Coolify

Esta guía usa software de despliegue de código abierto en un servidor propio. El dominio puede comprarse en Hostinger; se requiere además un VPS Linux (o servidor Linux propio) con acceso SSH, Docker y una IP pública. Coolify administra los contenedores, dominios, TLS y despliegues desde Git. La aplicación y la base de datos siguen corriendo en infraestructura que ustedes operan.

## Arquitectura

- `app.<dominio>`: frontend React/Vite, construido desde `Parking-Watch-Front-End/Dockerfile`.
- `api.<dominio>`: API Spring Boot, construida desde `Parking-Watch-Back-1/Dockerfile`.
- PostgreSQL/PostGIS: imagen `postgis/postgis:17-3.5`, en la red privada de Coolify y con volumen persistente.
- Edge: proceso ARM64 ejecutado en cada Raspberry Pi junto a su cámara; se conecta hacia `api.<dominio>` por HTTPS/WSS.

Railway no es necesario para esta opción. Si se usa Railway para Postgres, ya sería una plataforma administrada externa y se debe verificar que permita la extensión PostGIS requerida por las migraciones.

## 1. Preparar servidor y dominio

1. Crear un VPS Linux con al menos 2 vCPU, 4 GB RAM y 40 GB de disco para alojar Coolify, API, frontend y PostGIS; el tamaño debe crecer con la carga y almacenamiento de evidencias. Coolify por sí solo requiere 2 CPU, 2 GB RAM y 10 GB de disco.
2. Instalar Coolify siguiendo la [guía oficial de autohospedaje](https://coolify.io/docs/start-with-self-hosted). Conectar el VPS en Servers y comprobar que el proxy está iniciado.
3. En DNS del dominio, crear `A` para `app` y `api` con la IP pública del VPS. Si también se usará el dominio raíz, crear su `A` correspondiente. No apuntar el dominio a la IP de Coolify si la IP asignada es distinta de la del servidor de aplicaciones.
4. Abrir en el firewall solo SSH restringido a administradores y los puertos públicos 80/443. No abrir el puerto 5432 de Postgres ni el 8090 del Edge.

## 2. Crear PostgreSQL/PostGIS

En Coolify, crear un recurso de tipo **Service / Docker Compose** en el mismo servidor y red que el backend. Usar la imagen `postgis/postgis:17-3.5`, puerto interno `5432`, base y usuario dedicados, contraseña aleatoria y almacenamiento persistente en `/var/lib/postgresql/data`. No mapear el puerto a Internet. Configurar y probar backups fuera del VPS; un volumen por sí solo no es una copia de seguridad.

Anotar el nombre interno del contenedor/servicio y la base de datos para construir la conexión que utilizará el backend. Si el recurso usa la base `cupo`, el usuario `cupo` y contraseña `CAMBIAR`, una forma JDBC válida en la red privada es:

```text
jdbc:postgresql://<host-interno-postgres>:5432/cupo
```

## 3. Desplegar el backend

En Coolify, crear Application desde GitHub con el repositorio `OscarHernandezVel/Parking-Watch-Back-1`, rama `main`, build pack Dockerfile y ruta `Dockerfile`. Asignar `https://api.<dominio>` y puerto interno `8080`. Habilitar health check en `/actuator/health` si la versión de Coolify expone ese ajuste.

Configurar estas variables en el recurso Backend. Mantener `DATABASE_URL` en formato JDBC para evitar exponer la base fuera de la red privada:

| Variable | Valor |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://<host-interno-postgres>:5432/cupo` |
| `DATABASE_USERNAME` | Usuario configurado en PostGIS |
| `DATABASE_PASSWORD` | Contraseña larga y aleatoria |
| `JWT_SECRET` | Secreto aleatorio fuerte, único y privado |
| `CORS_ALLOWED_ORIGIN` | `https://app.<dominio>` sin barra final |
| `EDGE_DEVICE_TOKENS` | `CAM-MAQ-01=pwd_<token-aleatorio-largo>` |
| `STORAGE_PATH` | `/data` |
| `ADMIN_MFA_REQUIRED` | `true` |
| `BOOTSTRAP_ENABLED` | `true` para el primer arranque; luego cambiar a `false` |
| `BOOTSTRAP_ADMIN_USERNAME` | Nombre único del administrador inicial |
| `BOOTSTRAP_ADMIN_PASSWORD` | Contraseña aleatoria, guardar en gestor de contraseñas |
| `BOOTSTRAP_ADMIN_TOTP_SECRET` | Opcional; dejar vacío si se configura MFA tras iniciar sesión |

Agregar un volumen persistente para `/data`, donde el backend guarda archivos de evidencia. Tras el primer inicio y la creación de la cuenta administrativa, cambiar `BOOTSTRAP_ENABLED=false` y redeplegar. Nunca guardar estos secretos en Git ni en archivos `.env` versionados.

Verificar `https://api.<dominio>/actuator/health` antes de desplegar el frontend.

## 4. Desplegar el frontend

Crear Application desde `danielchauxgcampusucceduco/Parking-Watch-Front-End`, rama `ChauxBranch`, build pack Dockerfile y ruta `Dockerfile`. Asignar `https://app.<dominio>` y puerto interno `80`.

En **Build Arguments** configurar:

| Argumento | Valor |
|---|---|
| `VITE_API_URL` | `https://api.<dominio>` |
| `VITE_WS_URL` | `wss://api.<dominio>/ws` |

En las variables de ejecución del contenedor configurar:

| Variable | Valor |
|---|---|
| `API_ORIGIN` | `https://api.<dominio>` |
| `WS_ORIGIN` | `wss://api.<dominio>` |

Las URLs `VITE_*` se incorporan al bundle durante la compilación; al cambiarlas, volver a construir la imagen. El servidor Nginx del frontend sirve la SPA, cabeceras de seguridad y `/health`.

## 5. Edge en Raspberry Pi

El repositorio `OscarHernandezVel/Parking_Watch_Edge_2`, rama `edgeMain`, no se despliega en Coolify: ejecutar en Raspberry Pi OS de 64 bits junto a cada cámara. Instalar Java 21 y dependencias de cámara indicadas por el proyecto, compilar/publicar el artefacto ARM64 y configurar un servicio del sistema.

Variables mínimas del agente:

```dotenv
CAMERA_ID=CAM-MAQ-01
BACKEND_URL=https://api.<dominio>
DEVICE_TOKEN=pwd_<mismo-token-de-EDGE_DEVICE_TOKENS>
AGENT_MODE=CAMERA
HEALTH_PORT=8090
```

El puerto `8090` es solo para salud local y no se expone públicamente. Para más cámaras, registrar un `CAMERA_ID` y token independiente para cada equipo en `EDGE_DEVICE_TOKENS` y en su configuración local.

## 6. Comprobación y operación

1. Confirmar que PostGIS inició y que las migraciones Flyway del backend finalizaron.
2. Revisar `https://api.<dominio>/actuator/health`.
3. Abrir `https://app.<dominio>`, iniciar sesión y comprobar que el navegador no muestra errores CORS o WebSocket.
4. Iniciar Edge y verificar su conexión autenticada; revisar los logs del backend y del agente.
5. Configurar backups de base y evidencias fuera del VPS, actualizaciones de seguridad, alertas de disco y restauraciones periódicas.

## Nota de licencia

Un repositorio público no tiene licencia Open Source automáticamente. Los propietarios deben elegir y añadir la licencia antes de declarar el sistema Open Source.

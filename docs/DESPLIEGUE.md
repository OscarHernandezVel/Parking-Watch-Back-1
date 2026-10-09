# Despliegue de Parking Watch: Vercel + Render

Esta guía corresponde a los repositorios públicos:

- Frontend: [`Parking-Watch-Front-End`](https://github.com/danielchauxgcampusucceduco/Parking-Watch-Front-End), rama `ChauxBranch`.
- Backend: [`Parking-Watch-Back-1`](https://github.com/OscarHernandezVel/Parking-Watch-Back-1), rama `main`.
- Edge: [`Parking_Watch_Edge_2`](https://github.com/OscarHernandezVel/Parking_Watch_Edge_2), rama `edgeMain`.

Render aloja API y PostgreSQL; Vercel sirve la SPA; Edge se ejecuta en una Raspberry Pi y se conecta saliendo a la API por HTTPS/WSS. No se despliega el Edge en Render. No se requieren servicios ni llaves de AWS.

## 1. Orden de publicación

1. En Render, crear el Blueprint desde el repositorio `Parking-Watch-Back-1`, archivo `render.yaml`, rama `main`, región Virginia. El Blueprint crea el Web Service Docker, PostgreSQL y disco persistente.
2. Esperar que Render asigne el dominio del Web Service. El nombre configurado por defecto es `cupo-backend`; si Render asigna otro, usar el dominio real en los pasos siguientes.
3. En Vercel, importar `Parking-Watch-Front-End`, seleccionar `ChauxBranch` como Production Branch, framework Vite y directorio raíz `.`. Los comandos y salida están definidos por `vercel.json`.
4. En Vercel configurar las variables de la sección 3; luego desplegar. Copiar el dominio de producción de Vercel.
5. En Render establecer `CORS_ALLOWED_ORIGIN` con ese dominio exacto (`https://...vercel.app`), sin `/` final. Reiniciar/desplegar el backend.
6. Verificar `https://<dominio-render>/actuator/health` y el inicio de sesión en la aplicación.

Los repos ya son públicos en GitHub. Los cambios locales deben publicarse en sus ramas indicadas para que Render/Vercel reciban esta configuración. No subir archivos `.env`, tokens, contraseñas, secretos TOTP ni llaves privadas.

Frontend desplegado en Vercel: <https://parking-watch-front-end.vercel.app/> (Production, rama `ChauxBranch`).

## 2. Variables de Vercel

Configurarlas para **Production** y **Preview** en Settings → Environment Variables. Son valores públicos incluidos en el bundle del navegador; nunca poner credenciales aquí.

| Variable | Valor |
|---|---|
| `VITE_BACKEND_API_URL` | `https://cupo-backend-so90.onrender.com` |
| `VITE_BACKEND_WS_URL` | `wss://cupo-backend-so90.onrender.com/ws` |

Ejemplo si Render asigna el nombre previsto: `https://cupo-backend.onrender.com` y `wss://cupo-backend.onrender.com/ws`. El CSP de `vercel.json` permite conexiones al subdominio `*.onrender.com`; si se usa un dominio propio de API, agregar su origen HTTPS y WSS allí y volver a desplegar.

## 3. Variables de Render

El Blueprint genera `JWT_SECRET`, conecta `DATABASE_URL` a PostgreSQL, y configura `STORAGE_PATH=/data`. Completar las variables marcadas `sync: false` desde el panel del servicio:

| Variable | Qué configurar |
|---|---|
| `CORS_ALLOWED_ORIGIN` | `https://parking-watch-front-end.vercel.app` (sin barra final). |
| `EDGE_DEVICE_TOKENS` | `CAM-MAQ-01=pwd_<token-aleatorio-largo>`; el mismo token va en `DEVICE_TOKEN` del Edge. Genéralo con un gestor de secretos o `openssl rand -hex 32`, anteponiendo `pwd_`. |
| `BOOTSTRAP_ADMIN_USERNAME` | Nombre único del administrador inicial. |
| `BOOTSTRAP_ADMIN_PASSWORD` | Contraseña aleatoria, larga y única. Guardarla en un gestor de contraseñas. |

El Blueprint deja `BOOTSTRAP_ENABLED=true` y `ADMIN_MFA_REQUIRED=true`. Tras iniciar sesión, configurar el segundo factor. `BOOTSTRAP_ADMIN_TOTP_SECRET` solo se usa si se decide preconfigurar TOTP y se debe guardar como secreto.

Render también espera el primer despliegue manual si `autoDeploy: false`. Más adelante el workflow `Release` puede dispararlo con `RENDER_DEPLOY_HOOK_URL` una vez que se configure CI.

## 4. Secretos y variables de GitHub Actions

Configurar en Settings → Secrets and variables → Actions del repositorio correspondiente:

**Backend (`Parking-Watch-Back-1`)**

- Secret `NVD_API_KEY`: llave gratuita del servicio NVD, requerida por el job de análisis de dependencias.
- Secret `RENDER_DEPLOY_HOOK_URL`: deploy hook del servicio backend en Render.
- Secret `SMOKE_USERNAME` y `SMOKE_PASSWORD`: cuenta de operador para la prueba de humo; no usar la cuenta de administrador.
- Variable `API_URL`: URL HTTPS real del backend en Render, sin `/` final.

**Edge (`Parking_Watch_Edge_2`)**

- Secret `GH_PACKAGES_TOKEN`: token personal clásico de GitHub con permiso `read:packages`, emitido por quien tenga acceso al paquete `cupo-contracts` del repositorio Backend. Es necesario porque GitHub Packages requiere autenticación Maven entre repositorios. No reutilizar el token del dispositivo.

El token debe ser de mínimo alcance y almacenarse solamente en GitHub Actions. No crear token si no se va a compilar Edge mediante Actions.

## 5. Edge en Raspberry Pi

El proceso requiere Raspberry Pi OS de 64 bits y el hardware/dependencias indicadas por el proyecto. La rama `edgeMain` no contiene actualmente la carpeta `deploy/` ni los scripts `install.sh`/`update.sh` que menciona el workflow de Release; por eso esa publicación no puede instalarse siguiendo la guía antigua. Para la primera puesta en marcha, preparar el JAR ARM64 desde la Release una vez que exista, o compilarlo en la Raspberry con Java 21 y el perfil Maven del proyecto.

Variables mínimas del proceso Edge:

```dotenv
CAMERA_ID=CAM-MAQ-01
BACKEND_URL=https://<dominio-real-del-backend>
DEVICE_TOKEN=pwd_<el-mismo-token-configurado-en-Render>
AGENT_MODE=CAMERA
HEALTH_PORT=8090
```

El proceso también puede correr `AGENT_MODE=SIMULATION` cuando la configuración y fuente de video lo permitan. El puerto `8090` es local para salud/monitorización; no se expone públicamente. Configurar el proceso como servicio de sistema y proteger el archivo de entorno con permisos solo para el usuario del servicio.

## 6. Relación entre los tres repositorios

- Backend publica REST bajo `/api/v1`, WebSocket navegador en `/ws` y WebSocket Edge en `/ws/edge`.
- El navegador recibe URL REST y WSS desde las variables `VITE_*` de Vercel.
- Edge recibe URL del backend y token de dispositivo mediante `BACKEND_URL` y `DEVICE_TOKEN`.
- El contrato Maven `cupo-contracts` se publica desde el repositorio Backend. Edge consulta el paquete desde `OscarHernandezVel/Parking-Watch-Back-1`; para GitHub Actions necesita `GH_PACKAGES_TOKEN`.
- El workflow de frontend sigue la rama `ChauxBranch`; los de backend y Edge siguen `main` y `edgeMain`, respectivamente.

## 7. Archivos de entorno

- Front local: `Parking-Watch-Front-End/.env.example` → copiar a `.env.local`; mantener localhost para desarrollo local.
- Backend local: `Parking-Watch-Back-1/.env.example` → copiar a `.env`; reemplazar todos los valores de ejemplo antes de ejecutar.
- Edge: crear el archivo de entorno en la Raspberry a partir de las variables de esta guía. No commitearlo.

Los archivos de ejemplo no son llaves reales. Los valores de producción se agregan en los paneles de Render/Vercel y en GitHub Secrets, no en los archivos versionados.

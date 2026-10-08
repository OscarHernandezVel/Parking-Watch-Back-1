-- =============================================================================
-- Esquema inicial (Render Postgres con PostGIS), aplicado por Flyway al arrancar.
-- Corresponde a las tablas del documento técnico, con nombres en inglés:
--   camaras -> cameras, zonas_no_autorizadas -> no_parking_zones,
--   reportes -> reports, versiones_modelo -> model_versions,
--   estadisticas_minuto -> minute_statistics, usuarios -> users.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS postgis;

-- Funcionarios con acceso a la plataforma (PB-05).
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(80)  NOT NULL UNIQUE,
    full_name     VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(15)  NOT NULL CHECK (role IN ('OPERATOR', 'ADMINISTRATOR')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Cámaras del sistema; en la primera fase, la cámara de la maqueta (PB-01, PB-06).
CREATE TABLE cameras (
    id                VARCHAR(20)  PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    address           VARCHAR(100) NOT NULL,
    location          GEOMETRY(Point, 4326) NOT NULL,
    coverage_area     GEOMETRY(Polygon, 4326),
    video_url         VARCHAR(255) NOT NULL,
    status            VARCHAR(15)  NOT NULL DEFAULT 'OFFLINE' CHECK (status IN ('ONLINE', 'OFFLINE')),
    last_signal_at    TIMESTAMPTZ,
    frame_width       INTEGER      NOT NULL DEFAULT 1280 CHECK (frame_width > 0),
    frame_height      INTEGER      NOT NULL DEFAULT 720 CHECK (frame_height > 0),
    -- Hash SHA-256 del token del agente de borde (nunca se guarda el token en claro).
    device_token_hash CHAR(64)     NOT NULL UNIQUE,
    -- Se incrementa con cada cambio de zonas; el agente lo usa como ETag (RF-6.2).
    config_version    BIGINT       NOT NULL DEFAULT 1,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_cameras_location ON cameras USING GIST (location);

-- Zonas donde no se permite estacionar, dibujadas sobre la imagen (PB-04).
CREATE TABLE no_parking_zones (
    id                SERIAL       PRIMARY KEY,
    camera_id         VARCHAR(20)  NOT NULL REFERENCES cameras (id) ON DELETE CASCADE,
    name              VARCHAR(50)  NOT NULL,
    zone_type         VARCHAR(20)  NOT NULL CHECK (zone_type IN
                          ('SIDEWALK', 'CORNER', 'GARAGE_ENTRANCE', 'YELLOW_ZONE', 'TRAFFIC_LANE')),
    image_polygon     JSONB        NOT NULL,
    tolerance_seconds INTEGER      NOT NULL DEFAULT 60 CHECK (tolerance_seconds BETWEEN 1 AND 3600),
    signaled          BOOLEAN      NOT NULL DEFAULT FALSE,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_zone_name_per_camera UNIQUE (camera_id, name)
);

-- Versiones del modelo de IA y sus métricas (PB-13).
CREATE TABLE model_versions (
    version                 VARCHAR(20)  PRIMARY KEY,
    trained_at              TIMESTAMPTZ  NOT NULL,
    map50                   NUMERIC(4, 3) NOT NULL,
    precision_score         NUMERIC(4, 3) NOT NULL,
    recall                  NUMERIC(4, 3) NOT NULL,
    plate_accuracy          NUMERIC(4, 3) NOT NULL,
    detector_object_key     TEXT,
    detector_sha256         CHAR(64),
    plate_reader_object_key TEXT,
    plate_reader_sha256     CHAR(64),
    -- FALSE para versiones reportadas por el borde que no se registraron con métricas.
    verified                BOOLEAN      NOT NULL DEFAULT TRUE,
    active                  BOOLEAN      NOT NULL DEFAULT FALSE,
    registered_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Solo una versión activa a la vez.
CREATE UNIQUE INDEX ux_model_versions_single_active ON model_versions (active) WHERE active;

-- Historial de activaciones: permite volver a la versión anterior (RF-13.3).
CREATE TABLE model_activations (
    id           BIGSERIAL    PRIMARY KEY,
    version      VARCHAR(20)  NOT NULL REFERENCES model_versions (version),
    activated_by UUID         REFERENCES users (id),
    activated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Reportes de parqueo no autorizado (PB-03, PB-07).
CREATE TABLE reports (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    camera_id            VARCHAR(20)  NOT NULL REFERENCES cameras (id),
    zone_id              INTEGER      NOT NULL REFERENCES no_parking_zones (id),
    track_id             BIGINT       NOT NULL,
    vehicle_type         VARCHAR(12)  NOT NULL CHECK (vehicle_type IN ('CAR', 'MOTORCYCLE', 'BUS', 'TRUCK')),
    plate                VARCHAR(7),
    plate_confidence     NUMERIC(3, 2),
    plate_status         VARCHAR(25)  NOT NULL CHECK (plate_status IN ('READ', 'PENDING_CONFIRMATION', 'NOT_READ')),
    detection_confidence NUMERIC(3, 2) NOT NULL,
    entered_at           TIMESTAMPTZ  NOT NULL,
    generated_at         TIMESTAMPTZ  NOT NULL,
    exited_at            TIMESTAMPTZ,
    total_seconds        INTEGER,
    entry_photo_key      TEXT         NOT NULL,
    report_photo_key     TEXT         NOT NULL,
    plate_photo_key      TEXT         NOT NULL,
    status               VARCHAR(12)  NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'CONFIRMED', 'DISMISSED')),
    dismissal_reason     VARCHAR(40)  CHECK (dismissal_reason IN
                             ('FALSE_POSITIVE', 'MOMENTARY_STOP', 'EMERGENCY_VEHICLE', 'PLATE_MISREAD')),
    model_version        VARCHAR(20)  NOT NULL REFERENCES model_versions (version),
    reviewed_by          UUID         REFERENCES users (id),
    reviewed_at          TIMESTAMPTZ,
    source_event_id      UUID         NOT NULL UNIQUE,
    -- Un solo reporte por vehículo y por permanencia (RF-7.2).
    CONSTRAINT uq_reports_stay UNIQUE (camera_id, zone_id, track_id, entered_at),
    CONSTRAINT ck_reports_dismissal CHECK ((status = 'DISMISSED') = (dismissal_reason IS NOT NULL))
);
CREATE INDEX ix_reports_generated_at ON reports (generated_at DESC);
CREATE INDEX ix_reports_status ON reports (status);
CREATE INDEX ix_reports_zone ON reports (zone_id);

-- Idempotencia de eventos reenviados por el agente tras una desconexión (RNF-5.2).
CREATE TABLE ingested_events (
    event_id    UUID         PRIMARY KEY,
    camera_id   VARCHAR(20)  NOT NULL REFERENCES cameras (id),
    event_type  VARCHAR(25)  NOT NULL,
    outcome     VARCHAR(12)  NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Agregados en tiempo real guardados cada minuto (PB-12, RF-12.2).
CREATE TABLE minute_statistics (
    minute             TIMESTAMPTZ   NOT NULL,
    zone_id            INTEGER       NOT NULL REFERENCES no_parking_zones (id),
    vehicles_detected  INTEGER       NOT NULL CHECK (vehicles_detected >= 0),
    occupancy_pct      NUMERIC(5, 2) NOT NULL CHECK (occupancy_pct BETWEEN 0 AND 100),
    avg_dwell_seconds  INTEGER,
    fps                NUMERIC(5, 2) NOT NULL,
    avg_confidence     NUMERIC(3, 2) NOT NULL,
    PRIMARY KEY (minute, zone_id)
);

-- Alertas para el administrador: deriva del modelo, tasa de descartes y reentrenamiento (PB-14).
CREATE TABLE model_alerts (
    id              BIGSERIAL     PRIMARY KEY,
    alert_type      VARCHAR(30)   NOT NULL CHECK (alert_type IN ('CONFIDENCE_DRIFT', 'BRIGHTNESS_DRIFT',
                        'DISMISSAL_RATE_DRIFT', 'RETRAINING_REQUIRED', 'UNVERIFIED_MODEL')),
    camera_id       VARCHAR(20)   REFERENCES cameras (id),
    message         VARCHAR(255)  NOT NULL,
    observed_value  NUMERIC(10, 4),
    baseline_value  NUMERIC(10, 4),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    acknowledged_at TIMESTAMPTZ,
    acknowledged_by UUID          REFERENCES users (id)
);
CREATE INDEX ix_model_alerts_open ON model_alerts (alert_type) WHERE acknowledged_at IS NULL;

-- Muestras para reentrenamiento a partir de reportes descartados (RF-14.1).
-- Sin llave foránea: la muestra sobrevive al borrado del reporte a los 30 días (RNF-2.2).
CREATE TABLE retraining_samples (
    id               BIGSERIAL    PRIMARY KEY,
    report_id        UUID         NOT NULL UNIQUE,
    reason           VARCHAR(40)  NOT NULL,
    image_object_key TEXT         NOT NULL,
    label_object_key TEXT         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    consumed_at      TIMESTAMPTZ
);

-- Último estado en vivo de cada cámara (detecciones e indicadores) para la página web.
CREATE TABLE live_camera_state (
    camera_id      VARCHAR(20)  PRIMARY KEY REFERENCES cameras (id) ON DELETE CASCADE,
    detections     JSONB,
    detections_at  TIMESTAMPTZ,
    analytics      JSONB,
    analytics_at   TIMESTAMPTZ
);

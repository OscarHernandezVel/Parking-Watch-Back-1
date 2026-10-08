-- =============================================================================
-- Versión 6 del documento: despliegue en Render con canal WebSocket (PB-08).
--   * El video en vivo ya no se toma de una URL de la cámara: el backend retransmite por WebSocket
--     los fotogramas JPEG que envía el Edge (RF-2.1, RF-8.2).
--   * Los tokens de dispositivo llegan por la variable EDGE_DEVICE_TOKENS de Render (RNF-4.3); una
--     cámara puede existir antes de tener token asignado.
--   * El último estado en vivo (detecciones e indicadores) vive en memoria y se publica al instante;
--     el histórico sigue en minute_statistics (RF-9.2).
--   * Segundo factor (TOTP) para administradores (RNF-4.1).
-- =============================================================================

ALTER TABLE cameras DROP COLUMN video_url;
ALTER TABLE cameras ALTER COLUMN device_token_hash DROP NOT NULL;

DROP TABLE live_camera_state;

ALTER TABLE users
    ADD COLUMN totp_secret  VARCHAR(64),
    ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;

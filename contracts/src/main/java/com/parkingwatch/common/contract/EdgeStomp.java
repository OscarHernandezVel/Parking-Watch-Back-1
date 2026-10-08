package com.parkingwatch.common.contract;

/**
 * Destinos del canal WebSocket STOMP entre el Edge y el Backend (RF-8.1), documentados en la
 * especificación AsyncAPI del backend. Render solo publica tráfico HTTP/HTTPS, así que el canal en
 * tiempo real usa WSS en lugar de MQTT o WebRTC.
 */
public final class EdgeStomp {

  /** Endpoint WebSocket del Edge; el token del dispositivo viaja en el encabezado CONNECT. */
  public static final String ENDPOINT = "/ws/edge";

  /** Encabezado STOMP del CONNECT con el token del dispositivo ({@code Bearer pwd_...}). */
  public static final String AUTHORIZATION_HEADER = "Authorization";

  /** Eventos de cambio (entra a zona, supera la tolerancia, sale) como {@link EventBatch}. */
  public static final String EVENTS = "/app/edge/events";

  /** Detecciones cada 200 ms ({@link DetectionSnapshot}). */
  public static final String DETECTIONS = "/app/edge/detections";

  /** Fotograma JPEG reducido (960 x 540) para la vista en vivo, con cuerpo binario. */
  public static final String VIDEO = "/app/edge/video";

  /** Indicadores en tiempo real cada 5 s ({@link AnalyticsSnapshot}). */
  public static final String ANALYTICS = "/app/edge/analytics";

  /** Señal de vida ({@link HeartbeatMessage}). */
  public static final String HEARTBEAT = "/app/edge/heartbeat";

  /** Resumen por minuto para el histórico ({@link MinuteStatisticsMessage}). */
  public static final String STATISTICS = "/app/edge/statistics";

  /** Alerta de deriva calculada en el borde ({@link DriftAlertMessage}). */
  public static final String ALERTS = "/app/edge/alerts";

  /** Cola del Edge con los cambios de zonas y de modelo ({@link EdgeConfiguration}). */
  public static final String CONFIG_QUEUE = "/user/queue/config";

  /** Cola del Edge con el resultado de cada lote de eventos ({@link EventBatchResult}). */
  public static final String EVENT_RESULTS_QUEUE = "/user/queue/event-results";

  /** Tipo de contenido de los fotogramas de video (STOMP los transmite como trama binaria). */
  public static final String VIDEO_CONTENT_TYPE = "application/octet-stream";

  private EdgeStomp() {}

  /** Destino sin el prefijo {@code /user}, como lo usa el backend al enviar a un usuario. */
  public static String userDestination(String queue) {
    return queue.substring("/user".length());
  }
}

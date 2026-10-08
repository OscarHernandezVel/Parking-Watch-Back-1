package com.parkingwatch.backend.shared;

/**
 * Evento para las páginas abiertas (patrón Observer sobre los eventos de Spring). Tras confirmarse
 * la transacción, {@code RealtimeEventRelay} lo publica por WebSocket STOMP en el tópico {@code
 * channel} (RF-3.4, RF-8.2).
 *
 * @param channel tópico STOMP, p. ej. {@code /topic/reports} o {@code /topic/cameras/CAM-1/events}
 * @param name nombre del evento, p. ej. {@code report.created}
 * @param payload datos del evento
 */
public record RealtimeEvent(String channel, String name, Object payload) {

  /** Reportes nuevos y revisados (PB-03). */
  public static final String REPORTS = "/topic/reports";

  /** Alertas de deriva y reentrenamiento para el administrador (PB-16). */
  public static final String ALERTS = "/topic/alerts";

  /** Cambios de estado de las cámaras para el mapa (PB-01). */
  public static final String CAMERAS = "/topic/cameras";

  /** Eventos de zona de una cámara (entrada, tolerancia superada, salida). */
  public static String cameraChannel(String cameraId) {
    return CAMERAS + "/" + cameraId + "/events";
  }
}

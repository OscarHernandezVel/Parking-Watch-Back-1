package com.parkingwatch.backend.realtime;

/**
 * Tópicos STOMP a los que se suscriben los navegadores en {@code /ws} (RF-8.2), documentados en la
 * especificación AsyncAPI ({@code docs/asyncapi.yaml}).
 */
public final class BrowserTopics {

  /** Endpoint WebSocket de la página web; el JWT viaja en el encabezado CONNECT. */
  public static final String ENDPOINT = "/ws";

  /** Prefijo de los tópicos de difusión. */
  public static final String TOPIC_PREFIX = "/topic";

  private BrowserTopics() {}

  /** Fotogramas JPEG de la vista en vivo (cuerpo binario). */
  public static String video(String cameraId) {
    return camera(cameraId) + "/video";
  }

  /** Vehículos detectados con su recuadro, zona y tiempo detenido. */
  public static String detections(String cameraId) {
    return camera(cameraId) + "/detections";
  }

  /** Indicadores en tiempo real de la cámara (PB-14). */
  public static String analytics(String cameraId) {
    return camera(cameraId) + "/analytics";
  }

  private static String camera(String cameraId) {
    return TOPIC_PREFIX + "/cameras/" + cameraId;
  }
}

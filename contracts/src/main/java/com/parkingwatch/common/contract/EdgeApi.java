package com.parkingwatch.common.contract;

/**
 * Rutas HTTPS del contrato entre el Edge y el Backend (RF-8.3, RF-15.3). Ambos lados usan estas
 * constantes, de modo que una ruta no puede cambiar en uno solo de ellos. El tráfico en tiempo real
 * viaja por WebSocket STOMP con los destinos de {@link EdgeStomp}.
 */
public final class EdgeApi {

  public static final String BASE = "/api/v1/edge/cameras/{cameraId}";

  /** Configuración de zonas y modelo activo con ETag (al iniciar y como respaldo del WebSocket). */
  public static final String CONFIGURATION = "/configuration";

  /** Lote de eventos guardados en el búfer local mientras no hubo conexión (RF-8.3). */
  public static final String EVENTS_BATCH = "/events/batch";

  /** Subida multipart de las tres fotos de evidencia (partes entry, report y plate). */
  public static final String EVIDENCE = "/evidence";

  /** Descripción del modelo activo con la huella SHA-256 de cada archivo (RF-9.1). */
  public static final String ACTIVE_MODEL = "/model";

  /** Descarga de un archivo ONNX del modelo: {@code /models/{version}/{artifact}}. */
  public static final String MODEL_ARTIFACT = "/models/{version}/{artifact}";

  /** Nombre del archivo del detector de vehículos en {@link #MODEL_ARTIFACT}. */
  public static final String DETECTOR_ARTIFACT = "detector";

  /** Nombre del archivo del lector de placas en {@link #MODEL_ARTIFACT}. */
  public static final String PLATE_READER_ARTIFACT = "plate-reader";

  /** Prefijo del token de dispositivo que identifica a una cámara. */
  public static final String DEVICE_TOKEN_PREFIX = "pwd_";

  private EdgeApi() {}

  /** Ruta absoluta de un recurso para una cámara concreta. */
  public static String pathFor(String cameraId, String resource) {
    return BASE.replace("{cameraId}", cameraId) + resource;
  }

  /** Ruta absoluta de descarga de un archivo del modelo. */
  public static String modelArtifactPath(String cameraId, String version, String artifact) {
    return pathFor(
        cameraId, MODEL_ARTIFACT.replace("{version}", version).replace("{artifact}", artifact));
  }
}

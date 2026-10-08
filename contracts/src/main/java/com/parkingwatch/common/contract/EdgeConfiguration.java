package com.parkingwatch.common.contract;

import com.parkingwatch.common.domain.ZoneType;
import java.util.List;

/**
 * Configuración de la cámara (RF-6.2): zonas activas, modelo vigente con la huella de sus archivos
 * y parámetros de comunicación. El backend la entrega al conectarse el Edge y la empuja por
 * WebSocket ({@link EdgeStomp#CONFIG_QUEUE}) con cada cambio; el Edge la aplica sin reiniciarse.
 */
public record EdgeConfiguration(
    String cameraId,
    long configVersion,
    FrameSize frame,
    List<ZoneDefinition> zones,
    ActiveModel model,
    Settings settings) {

  /** Copia defensiva de la lista. */
  public EdgeConfiguration {
    zones = List.copyOf(zones);
  }

  /** Resolución de la imagen sobre la que se dibujaron las zonas. */
  public record FrameSize(int width, int height) {}

  /** Punto de un polígono en píxeles. */
  public record Point(double x, double y) {}

  /** Zona no autorizada con su polígono, tolerancia y señalización. */
  public record ZoneDefinition(
      long id,
      String name,
      ZoneType zoneType,
      List<Point> polygon,
      int toleranceSeconds,
      boolean signaled) {

    /** Copia defensiva del polígono. */
    public ZoneDefinition {
      polygon = List.copyOf(polygon);
    }
  }

  /** Versión activa del modelo y sus artefactos ONNX (pueden faltar en la maqueta). */
  public record ActiveModel(String version, ModelArtifact detector, ModelArtifact plateReader) {}

  /**
   * Archivo del modelo: ruta de descarga en el backend (relativa a su URL base y autenticada con el
   * token del dispositivo) y SHA-256 para verificar su integridad (RF-15.3).
   */
  public record ModelArtifact(String url, String sha256) {}

  /**
   * Intervalos de comunicación con el backend y parámetros de la vista en vivo (RF-11.4).
   *
   * @param heartbeatIntervalSeconds señal de vida (el backend marca sin conexión a los 30 s)
   * @param detectionSampleIntervalMs envío de detecciones (200 ms)
   * @param analyticsIntervalSeconds envío de indicadores (5 s)
   * @param configPollIntervalSeconds consulta de respaldo de la configuración por HTTPS
   * @param video tamaño, velocidad y calidad del video en vivo
   */
  public record Settings(
      int heartbeatIntervalSeconds,
      int detectionSampleIntervalMs,
      int analyticsIntervalSeconds,
      int configPollIntervalSeconds,
      LiveVideo video) {

    /** Valores del documento: detecciones cada 200 ms y video de 960 x 540 a 6 fps. */
    public static Settings defaults() {
      return new Settings(5, 200, 5, 60, LiveVideo.defaults());
    }
  }

  /**
   * Copia reducida del video para la vista en vivo (RF-2.1): JPEG de 960 x 540 px a 5-8 fps.
   *
   * @param width ancho en píxeles
   * @param height alto en píxeles
   * @param fps fotogramas por segundo
   * @param jpegQuality calidad JPEG entre 0 y 1
   */
  public record LiveVideo(int width, int height, int fps, double jpegQuality) {

    /** 960 x 540 px a 6 fps con calidad 0,7. */
    public static LiveVideo defaults() {
      return new LiveVideo(960, 540, 6, 0.7);
    }
  }
}

package com.parkingwatch.backend.bootstrap;

import com.parkingwatch.backend.zone.ImagePoint;
import com.parkingwatch.backend.zone.ZoneDraft;
import com.parkingwatch.common.domain.ZoneType;
import java.util.List;

/**
 * Datos de la maqueta (60 x 90 cm) en coordenadas de una imagen de 1280 x 720 px. Son valores
 * iniciales: el administrador las redibuja sobre la captura real (PB-04). En la maqueta la
 * tolerancia es de 10 s (RF-7.1).
 */
public final class PrototypeData {

  public static final String CAMERA_ID = "CAM-MAQ-01";
  public static final String MODEL_VERSION = "det-1.0-maqueta";
  private static final int TOLERANCE = 10;

  private PrototypeData() {}

  /** Zonas de la maqueta: andén, esquina, garaje, zona amarilla y carril (sin señalizar). */
  public static List<ZoneDraft> zones() {
    return List.of(
        zone("Zona amarilla", ZoneType.YELLOW_ZONE, true, 820, 420, 1180, 560),
        zone("Entrada de garaje", ZoneType.GARAGE_ENTRANCE, true, 420, 430, 600, 560),
        zone("Esquina", ZoneType.CORNER, true, 60, 420, 260, 600),
        zone("Andén norte", ZoneType.SIDEWALK, true, 0, 120, 1280, 200),
        zone("Carril de circulación", ZoneType.TRAFFIC_LANE, false, 0, 260, 1280, 400));
  }

  private static ZoneDraft zone(
      String name, ZoneType type, boolean signaled, double x1, double y1, double x2, double y2) {
    List<ImagePoint> rectangle =
        List.of(
            new ImagePoint(x1, y1),
            new ImagePoint(x2, y1),
            new ImagePoint(x2, y2),
            new ImagePoint(x1, y2));
    return new ZoneDraft(null, name, type, rectangle, TOLERANCE, signaled);
  }
}

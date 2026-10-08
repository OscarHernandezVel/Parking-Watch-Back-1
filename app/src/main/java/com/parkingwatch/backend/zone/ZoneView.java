package com.parkingwatch.backend.zone;

import com.parkingwatch.common.domain.ZoneType;
import java.util.List;

/** Zona tal como la consulta la página web. */
public record ZoneView(
    long id,
    String cameraId,
    String name,
    ZoneType zoneType,
    List<ImagePoint> polygon,
    int toleranceSeconds,
    boolean signaled) {

  /** Copia defensiva del polígono. */
  public ZoneView {
    polygon = List.copyOf(polygon);
  }

  static ZoneView of(NoParkingZone zone) {
    return new ZoneView(
        zone.getId(),
        zone.getCameraId(),
        zone.getName(),
        zone.getZoneType(),
        zone.getPolygon(),
        zone.getToleranceSeconds(),
        zone.isSignaled());
  }
}

package com.parkingwatch.backend.camera;

import com.parkingwatch.common.contract.EdgeConfiguration.FrameSize;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.locationtech.jts.geom.Polygon;

/** Cámara tal como la ve la página web: ubicación, área cubierta y estado (PB-01). */
public record CameraView(
    String id,
    String name,
    String address,
    GeoPoint location,
    List<GeoPoint> coverageArea,
    CameraDisplayStatus displayStatus,
    Instant lastSignalAt,
    FrameSize frame) {

  /** Copia defensiva de la lista. */
  public CameraView {
    coverageArea = coverageArea == null ? null : List.copyOf(coverageArea);
  }

  /** Coordenada WGS84. */
  public record GeoPoint(double latitude, double longitude) {}

  static CameraView of(Camera camera, CameraDisplayStatus status) {
    return new CameraView(
        camera.getId(),
        camera.getName(),
        camera.getAddress(),
        new GeoPoint(camera.getLocation().getY(), camera.getLocation().getX()),
        ring(camera.getCoverageArea()),
        status,
        camera.getLastSignalAt(),
        new FrameSize(camera.getFrameWidth(), camera.getFrameHeight()));
  }

  private static List<GeoPoint> ring(Polygon polygon) {
    if (polygon == null) {
      return List.of();
    }
    return Arrays.stream(polygon.getExteriorRing().getCoordinates())
        .map(coordinate -> new GeoPoint(coordinate.y, coordinate.x))
        .toList();
  }
}

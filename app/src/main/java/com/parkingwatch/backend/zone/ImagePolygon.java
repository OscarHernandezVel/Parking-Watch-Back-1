package com.parkingwatch.backend.zone;

import com.parkingwatch.backend.shared.ValidationException;
import java.util.List;

/**
 * Polígono de una zona dibujado sobre la imagen de la cámara (RF-4.1). Objeto de valor que valida
 * vértices, límites del cuadro, auto-intersección y área mínima al construirse.
 */
public final class ImagePolygon {

  private static final int MIN_VERTICES = 3;
  private static final int MAX_VERTICES = 64;
  private static final double MIN_AREA_PX = 100;

  private final List<ImagePoint> vertices;

  private ImagePolygon(List<ImagePoint> vertices) {
    this.vertices = List.copyOf(vertices);
  }

  /** Crea y valida el polígono para un cuadro de ancho y alto dados. */
  public static ImagePolygon of(List<ImagePoint> points, int frameWidth, int frameHeight) {
    assertVertexCount(points);
    for (int i = 0; i < points.size(); i++) {
      assertInsideFrame(points.get(i), frameWidth, frameHeight, i);
    }
    ImagePolygon polygon = new ImagePolygon(points);
    if (polygon.isSelfIntersecting()) {
      throw new ValidationException("El polígono no puede cruzarse a sí mismo");
    }
    if (polygon.area() < MIN_AREA_PX) {
      throw new ValidationException("El área del polígono debe ser de al menos 100 px²");
    }
    return polygon;
  }

  private static void assertVertexCount(List<ImagePoint> points) {
    if (points == null || points.size() < MIN_VERTICES || points.size() > MAX_VERTICES) {
      throw new ValidationException(
          "El polígono debe tener entre " + MIN_VERTICES + " y " + MAX_VERTICES + " vértices");
    }
  }

  public List<ImagePoint> vertices() {
    return vertices;
  }

  /** Área por la fórmula del zapato. */
  public double area() {
    double doubled = 0;
    for (int i = 0; i < vertices.size(); i++) {
      ImagePoint a = vertices.get(i);
      ImagePoint b = vertices.get((i + 1) % vertices.size());
      doubled += a.x() * b.y() - b.x() * a.y();
    }
    return Math.abs(doubled) / 2;
  }

  /** Prueba de pertenencia por el método del rayo. */
  public boolean contains(ImagePoint point) {
    boolean inside = false;
    for (int i = 0, j = vertices.size() - 1; i < vertices.size(); j = i++) {
      ImagePoint a = vertices.get(i);
      ImagePoint b = vertices.get(j);
      boolean crosses = (a.y() > point.y()) != (b.y() > point.y());
      if (crosses && point.x() < (b.x() - a.x()) * (point.y() - a.y()) / (b.y() - a.y()) + a.x()) {
        inside = !inside;
      }
    }
    return inside;
  }

  /** Indica si dos aristas no adyacentes se cruzan. */
  public boolean isSelfIntersecting() {
    int n = vertices.size();
    for (int i = 0; i < n; i++) {
      for (int j = i + 1; j < n; j++) {
        boolean adjacent = j == i + 1 || (i == 0 && j == n - 1);
        if (!adjacent && segmentsIntersect(i, j)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean segmentsIntersect(int first, int second) {
    ImagePoint p1 = vertices.get(first);
    ImagePoint q1 = vertices.get((first + 1) % vertices.size());
    ImagePoint p2 = vertices.get(second);
    ImagePoint q2 = vertices.get((second + 1) % vertices.size());
    int o1 = orientation(p1, q1, p2);
    int o2 = orientation(p1, q1, q2);
    int o3 = orientation(p2, q2, p1);
    int o4 = orientation(p2, q2, q1);
    if (o1 != o2 && o3 != o4) {
      return true;
    }
    return touches(o1, p1, q1, p2)
        || touches(o2, p1, q1, q2)
        || touches(o3, p2, q2, p1)
        || touches(o4, p2, q2, q1);
  }

  /** Caso colineal: el punto está sobre el segmento a-b. */
  private static boolean touches(int orientation, ImagePoint a, ImagePoint b, ImagePoint point) {
    return orientation == 0 && onSegment(a, b, point);
  }

  private static int orientation(ImagePoint a, ImagePoint b, ImagePoint c) {
    double value = (b.y() - a.y()) * (c.x() - b.x()) - (b.x() - a.x()) * (c.y() - b.y());
    return (int) Math.signum(value);
  }

  private static boolean onSegment(ImagePoint a, ImagePoint b, ImagePoint p) {
    return Math.min(a.x(), b.x()) <= p.x()
        && p.x() <= Math.max(a.x(), b.x())
        && Math.min(a.y(), b.y()) <= p.y()
        && p.y() <= Math.max(a.y(), b.y());
  }

  private static void assertInsideFrame(ImagePoint point, int width, int height, int index) {
    boolean valid =
        Double.isFinite(point.x())
            && Double.isFinite(point.y())
            && point.x() >= 0
            && point.y() >= 0
            && point.x() <= width
            && point.y() <= height;
    if (!valid) {
      throw new ValidationException(
          "El vértice " + index + " está fuera del cuadro de " + width + "x" + height);
    }
  }
}

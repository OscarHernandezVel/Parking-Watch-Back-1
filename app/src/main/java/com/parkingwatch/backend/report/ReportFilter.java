package com.parkingwatch.backend.report;

import com.parkingwatch.common.domain.ReportStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros de la lista de reportes (RF-3.1) traducidos a una Specification de JPA (patrón
 * Specification): cada filtro presente agrega un predicado.
 */
public record ReportFilter(
    Instant from, Instant to, String cameraId, Long zoneId, ReportStatus status) {

  /** Construye la especificación combinando los filtros presentes. */
  public Specification<Report> toSpecification() {
    return (root, query, builder) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (from != null) {
        predicates.add(builder.greaterThanOrEqualTo(root.get("generatedAt"), from));
      }
      if (to != null) {
        predicates.add(builder.lessThan(root.get("generatedAt"), to));
      }
      if (cameraId != null) {
        predicates.add(builder.equal(root.get("cameraId"), cameraId));
      }
      if (zoneId != null) {
        predicates.add(builder.equal(root.get("zoneId"), zoneId));
      }
      if (status != null) {
        predicates.add(builder.equal(root.get("status"), status));
      }
      return builder.and(predicates.toArray(Predicate[]::new));
    };
  }

  /** Descripción legible para el encabezado de las exportaciones. */
  public String describe() {
    List<String> parts = new ArrayList<>();
    if (from != null) {
      parts.add("desde " + from);
    }
    if (to != null) {
      parts.add("hasta " + to);
    }
    if (cameraId != null) {
      parts.add("cámara " + cameraId);
    }
    if (zoneId != null) {
      parts.add("zona " + zoneId);
    }
    if (status != null) {
      parts.add("estado " + status);
    }
    return parts.isEmpty() ? "Sin filtros" : "Filtros: " + String.join(", ", parts);
  }
}

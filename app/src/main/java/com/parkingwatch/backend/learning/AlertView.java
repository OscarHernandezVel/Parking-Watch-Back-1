package com.parkingwatch.backend.learning;

import com.parkingwatch.common.domain.AlertType;
import java.time.Instant;

/** Alerta para la página del administrador. */
public record AlertView(
    long id,
    AlertType alertType,
    String cameraId,
    String message,
    Double observedValue,
    Double baselineValue,
    Instant createdAt,
    Instant acknowledgedAt) {

  static AlertView of(Alert alert) {
    return new AlertView(
        alert.getId(),
        alert.getAlertType(),
        alert.getCameraId(),
        alert.getMessage(),
        alert.getObservedValue(),
        alert.getBaselineValue(),
        alert.getCreatedAt(),
        alert.getAcknowledgedAt());
  }
}

package com.parkingwatch.common.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Alerta de deriva calculada en el borde: confianza o brillo fuera de lo normal (RF-14.2). */
public record DriftAlertMessage(
    @NotNull Type alertType,
    double observedValue,
    double baselineValue,
    @NotNull Instant detectedAt,
    @NotBlank @Size(max = 255) String message) {

  /** Tipos de deriva que el agente puede detectar. */
  public enum Type {
    CONFIDENCE_DRIFT,
    BRIGHTNESS_DRIFT
  }
}

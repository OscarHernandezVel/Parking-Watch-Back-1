package com.parkingwatch.common.domain;

/** Alertas para el administrador sobre el modelo y el reentrenamiento (PB-14). */
public enum AlertType {
  CONFIDENCE_DRIFT,
  BRIGHTNESS_DRIFT,
  DISMISSAL_RATE_DRIFT,
  RETRAINING_REQUIRED,
  UNVERIFIED_MODEL;

  /** Alertas de deriva de datos: cualquiera abierta activa el reentrenamiento (RF-14.3). */
  public boolean isDrift() {
    return this == CONFIDENCE_DRIFT || this == BRIGHTNESS_DRIFT || this == DISMISSAL_RATE_DRIFT;
  }
}

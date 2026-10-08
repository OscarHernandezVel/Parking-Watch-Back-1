package com.parkingwatch.common.domain;

/** Motivos de descarte de un reporte (RF-3.3). */
public enum DismissalReason {
  FALSE_POSITIVE,
  MOMENTARY_STOP,
  EMERGENCY_VEHICLE,
  PLATE_MISREAD;

  /** Indica si el descarte alimenta el dataset de reentrenamiento (RF-14.1). */
  public boolean feedsRetraining() {
    return this == FALSE_POSITIVE || this == PLATE_MISREAD;
  }
}

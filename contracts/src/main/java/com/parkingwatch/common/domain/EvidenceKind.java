package com.parkingwatch.common.domain;

/** Fotos de evidencia de un reporte (RF-3.2, RF-7.3). */
public enum EvidenceKind {
  /** Foto al entrar el vehículo a la zona. */
  ENTRY,
  /** Foto al cumplirse la tolerancia. */
  REPORT,
  /** Recorte ampliado de la placa. */
  PLATE
}

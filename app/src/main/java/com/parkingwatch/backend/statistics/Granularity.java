package com.parkingwatch.backend.statistics;

/** Agrupación de los agregados: por minuto, hora o día (PB-12). */
public enum Granularity {
  MINUTE("minute"),
  HOUR("hour"),
  DAY("day");

  private final String truncUnit;

  Granularity(String truncUnit) {
    this.truncUnit = truncUnit;
  }

  /** Unidad para la función date_trunc de PostgreSQL. */
  public String truncUnit() {
    return truncUnit;
  }
}

package com.parkingwatch.common.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Agregados de un minuto por zona, que el backend guarda para tendencias (RF-12.2). */
public record MinuteStatisticsMessage(
    @NotNull Instant minute, @NotNull @Size(max = 100) List<@Valid @NotNull ZoneMinute> zones) {

  /** Copia defensiva de la lista. */
  public MinuteStatisticsMessage {
    zones = zones == null ? null : List.copyOf(zones);
  }

  /** Indicadores de una zona en el minuto. */
  public record ZoneMinute(
      @Positive long zoneId,
      @PositiveOrZero int vehiclesDetected,
      @DecimalMin("0.0") @DecimalMax("100.0") double occupancyPct,
      @PositiveOrZero Integer avgDwellSeconds,
      @PositiveOrZero double fps,
      @DecimalMin("0.0") @DecimalMax("1.0") double avgConfidence) {}
}

package com.parkingwatch.common.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;

/** Indicadores en tiempo real sobre ventanas de 1 y 15 minutos (RF-12.1). */
public record AnalyticsSnapshot(
    @NotNull Instant generatedAt,
    @PositiveOrZero double fps,
    @PositiveOrZero double inferenceLatencyMs,
    @NotNull @Valid WindowMetrics oneMinute,
    @NotNull @Valid WindowMetrics fifteenMinutes) {

  /** Indicadores de una ventana deslizante. */
  public record WindowMetrics(
      @PositiveOrZero int vehiclesDetected,
      @PositiveOrZero Double avgDwellSeconds,
      @PositiveOrZero int reportsGenerated,
      @NotNull List<@Valid @NotNull ZoneOccupancy> zoneOccupancy) {

    /** Copia defensiva de la lista. */
    public WindowMetrics {
      zoneOccupancy = zoneOccupancy == null ? null : List.copyOf(zoneOccupancy);
    }
  }

  /** Porcentaje del tiempo con un vehículo detenido en la zona. */
  public record ZoneOccupancy(
      @Positive long zoneId, @DecimalMin("0.0") @DecimalMax("100.0") double occupancyPct) {}
}

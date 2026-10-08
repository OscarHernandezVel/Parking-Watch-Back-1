package com.parkingwatch.common.contract;

import com.parkingwatch.common.domain.VehicleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Muestra de detecciones para dibujar recuadros sobre el video en vivo (RF-2.2). */
public record DetectionSnapshot(
    @NotNull Instant capturedAt,
    @Positive int frameWidth,
    @Positive int frameHeight,
    @NotNull @Size(max = 100) List<@Valid @NotNull TrackedVehicle> vehicles) {

  /** Copia defensiva de la lista. */
  public DetectionSnapshot {
    vehicles = vehicles == null ? null : List.copyOf(vehicles);
  }

  /** Vehículo seguido con su recuadro, zona actual y tiempo inmóvil. */
  public record TrackedVehicle(
      @PositiveOrZero long trackId,
      @NotNull VehicleType vehicleType,
      @NotNull @Valid Box box,
      @DecimalMin("0.0") @DecimalMax("1.0") double confidence,
      @Positive Long zoneId,
      @PositiveOrZero double stationarySeconds) {}

  /** Recuadro en píxeles de la imagen original. */
  public record Box(double x1, double y1, double x2, double y2) {}
}

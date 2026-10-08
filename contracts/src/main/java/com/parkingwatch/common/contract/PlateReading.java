package com.parkingwatch.common.contract;

import com.parkingwatch.common.domain.PlateStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Lectura de placa resultante de la votación temporal (RF-11.2). */
public record PlateReading(
    @Size(max = 7) String value,
    @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
    @NotNull PlateStatus status) {

  /** Lectura vacía cuando el OCR no logró leer la placa. */
  public static PlateReading notRead() {
    return new PlateReading(null, null, PlateStatus.NOT_READ);
  }
}

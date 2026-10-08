package com.parkingwatch.backend.zone;

import com.parkingwatch.common.domain.ZoneType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Zona enviada por el administrador al guardar la configuración (RF-4.1, RF-4.2). Sin {@code id} se
 * crea; con {@code id} se actualiza.
 */
public record ZoneDraft(
    @Positive Long id,
    @NotBlank @Size(max = 50) String name,
    @NotNull ZoneType zoneType,
    @NotNull @Size(min = 3, max = 64) List<@NotNull ImagePoint> polygon,
    @Min(1) @Max(3600) int toleranceSeconds,
    boolean signaled) {

  /** Copia defensiva del polígono. */
  public ZoneDraft {
    polygon = polygon == null ? null : List.copyOf(polygon);
  }
}

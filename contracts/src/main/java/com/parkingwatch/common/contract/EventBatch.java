package com.parkingwatch.common.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Lote de eventos enviado en una sola petición (máximo 50). */
public record EventBatch(
    @NotNull @Size(min = 1, max = 50) List<@Valid @NotNull ParkingEventMessage> events) {

  /** Copia defensiva de la lista. */
  public EventBatch {
    events = events == null ? null : List.copyOf(events);
  }
}

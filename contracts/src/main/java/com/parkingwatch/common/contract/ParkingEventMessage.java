package com.parkingwatch.common.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.parkingwatch.common.domain.ParkingEventType;
import com.parkingwatch.common.domain.VehicleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de cambio de un vehículo en una zona (RF-9.4). El identificador {@code eventId} hace
 * idempotente el reenvío tras una desconexión (RNF-5.2). Solo TOLERANCE_EXCEEDED lleva placa y
 * evidencias.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParkingEventMessage(
    @NotNull UUID eventId,
    @NotNull ParkingEventType eventType,
    @Positive long zoneId,
    @PositiveOrZero long trackId,
    @NotNull VehicleType vehicleType,
    @DecimalMin("0.0") @DecimalMax("1.0") double detectionConfidence,
    @NotBlank @Size(max = 20) String modelVersion,
    @NotNull Instant enteredAt,
    @NotNull Instant occurredAt,
    @PositiveOrZero double dwellSeconds,
    @Valid PlateReading plate,
    @Valid EvidenceKeys evidence) {

  /** Un evento de tolerancia superada debe traer la placa y las tres evidencias. */
  @JsonIgnore
  @AssertTrue(message = "TOLERANCE_EXCEEDED requiere placa y evidencias")
  public boolean isComplete() {
    return eventType != ParkingEventType.TOLERANCE_EXCEEDED || (plate != null && evidence != null);
  }
}

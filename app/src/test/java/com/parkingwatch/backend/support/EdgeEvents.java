package com.parkingwatch.backend.support;

import com.parkingwatch.common.contract.EvidenceKeys;
import com.parkingwatch.common.contract.ParkingEventMessage;
import com.parkingwatch.common.contract.PlateReading;
import com.parkingwatch.common.domain.ParkingEventType;
import com.parkingwatch.common.domain.PlateStatus;
import com.parkingwatch.common.domain.VehicleType;
import java.time.Instant;
import java.util.UUID;

/** Constructor de eventos del agente para las pruebas (patrón Test Data Builder). */
public final class EdgeEvents {

  public static final Instant ENTERED = Instant.parse("2026-10-01T14:59:00Z");

  private long zoneId = 1;
  private long trackId = 41;
  private VehicleType vehicleType = VehicleType.CAR;
  private double confidence = 0.94;
  private String plate = "ABC123";
  private Instant enteredAt = ENTERED;
  private double dwellSeconds = 12;
  private EvidenceKeys evidence =
      new EvidenceKeys(
          "evidence/CAM-MAQ-01/2026/10/01/a-entry.jpg",
          "evidence/CAM-MAQ-01/2026/10/01/a-report.jpg",
          "evidence/CAM-MAQ-01/2026/10/01/a-plate.jpg");

  public static EdgeEvents event() {
    return new EdgeEvents();
  }

  public EdgeEvents zone(long value) {
    zoneId = value;
    return this;
  }

  public EdgeEvents track(long value) {
    trackId = value;
    return this;
  }

  public EdgeEvents vehicle(VehicleType value) {
    vehicleType = value;
    return this;
  }

  public EdgeEvents confidence(double value) {
    confidence = value;
    return this;
  }

  public EdgeEvents plate(String value) {
    plate = value;
    return this;
  }

  public EdgeEvents dwell(double value) {
    dwellSeconds = value;
    return this;
  }

  public EdgeEvents evidence(EvidenceKeys value) {
    evidence = value;
    return this;
  }

  public EdgeEvents enteredAt(Instant value) {
    enteredAt = value;
    return this;
  }

  /** Evento de tolerancia superada. */
  public ParkingEventMessage exceeded() {
    return build(
        ParkingEventType.TOLERANCE_EXCEEDED, enteredAt.plusMillis((long) (dwellSeconds * 1000)));
  }

  public ParkingEventMessage entered() {
    return build(ParkingEventType.ZONE_ENTERED, enteredAt);
  }

  /** Evento de salida a los segundos indicados desde la entrada. */
  public ParkingEventMessage exited(long afterSeconds) {
    return build(ParkingEventType.ZONE_EXITED, enteredAt.plusSeconds(afterSeconds));
  }

  private ParkingEventMessage build(ParkingEventType type, Instant occurredAt) {
    boolean exceeded = type == ParkingEventType.TOLERANCE_EXCEEDED;
    return new ParkingEventMessage(
        UUID.randomUUID(),
        type,
        zoneId,
        trackId,
        vehicleType,
        confidence,
        "det-1.0-maqueta",
        enteredAt,
        occurredAt,
        dwellSeconds,
        exceeded ? new PlateReading(plate, 0.91, PlateStatus.READ) : null,
        exceeded ? evidence : null);
  }
}

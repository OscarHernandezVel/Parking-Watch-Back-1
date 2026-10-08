package com.parkingwatch.backend.ingestion;

import com.parkingwatch.common.contract.EventResult;
import com.parkingwatch.common.domain.ParkingEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Evento ya procesado: garantiza idempotencia ante reenvíos del agente (RNF-5.2). */
@Entity
@Table(name = "ingested_events")
public class IngestedEvent {

  @Id
  @Column(name = "event_id")
  private UUID eventId;

  @Column(name = "camera_id", nullable = false, length = 20)
  private String cameraId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 25)
  private ParkingEventType eventType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private EventResult.Outcome outcome;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  protected IngestedEvent() {}

  /** Registra el desenlace de un evento. */
  public IngestedEvent(
      UUID eventId,
      String cameraId,
      ParkingEventType eventType,
      EventResult.Outcome outcome,
      Instant receivedAt) {
    this.eventId = eventId;
    this.cameraId = cameraId;
    this.eventType = eventType;
    this.outcome = outcome;
    this.receivedAt = receivedAt;
  }
}

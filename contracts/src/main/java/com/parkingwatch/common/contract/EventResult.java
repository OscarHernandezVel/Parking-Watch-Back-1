package com.parkingwatch.common.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/** Resultado del procesamiento de un evento en el backend. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventResult(UUID eventId, Outcome outcome, String reason, UUID reportId) {

  /** Desenlace del evento: el agente descarta del buzón local los aceptados y duplicados. */
  public enum Outcome {
    ACCEPTED,
    DUPLICATE,
    REJECTED
  }

  public static EventResult accepted(UUID eventId, UUID reportId) {
    return new EventResult(eventId, Outcome.ACCEPTED, null, reportId);
  }

  public static EventResult duplicate(UUID eventId, String reason) {
    return new EventResult(eventId, Outcome.DUPLICATE, reason, null);
  }

  public static EventResult rejected(UUID eventId, String reason) {
    return new EventResult(eventId, Outcome.REJECTED, reason, null);
  }
}

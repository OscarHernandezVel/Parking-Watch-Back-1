package com.parkingwatch.backend.ingestion;

import com.parkingwatch.common.contract.EventResult;
import java.util.UUID;

/** Resultado de un manejador de eventos. */
public record HandlerResult(EventResult.Outcome outcome, String reason, UUID reportId) {

  public static HandlerResult accepted(UUID reportId) {
    return new HandlerResult(EventResult.Outcome.ACCEPTED, null, reportId);
  }

  public static HandlerResult rejected(String reason) {
    return new HandlerResult(EventResult.Outcome.REJECTED, reason, null);
  }

  public static HandlerResult duplicate(String reason) {
    return new HandlerResult(EventResult.Outcome.DUPLICATE, reason, null);
  }

  public boolean isAccepted() {
    return outcome == EventResult.Outcome.ACCEPTED;
  }
}

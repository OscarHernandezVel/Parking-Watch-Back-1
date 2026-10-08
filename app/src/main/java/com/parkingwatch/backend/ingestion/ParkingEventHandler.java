package com.parkingwatch.backend.ingestion;

import com.parkingwatch.common.contract.ParkingEventMessage;
import com.parkingwatch.common.domain.ParkingEventType;
import java.time.Clock;
import java.time.Duration;

/**
 * Patrón Template Method: algoritmo común a todos los eventos (validar línea de tiempo, procesar,
 * acciones posteriores) con el paso específico en cada subclase. Cada subclase es además una
 * Strategy que el servicio de ingesta selecciona por tipo de evento.
 */
public abstract class ParkingEventHandler {

  /** Desfase máximo aceptado entre el reloj del agente y el del backend. */
  private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

  private final Clock clock;

  protected ParkingEventHandler(Clock clock) {
    this.clock = clock;
  }

  /** Tipo de evento que atiende el manejador. */
  public abstract ParkingEventType eventType();

  /** Procesa el evento dentro de la transacción del servicio de ingesta. */
  public final HandlerResult handle(String cameraId, ParkingEventMessage event) {
    String timelineProblem = checkTimeline(event);
    if (timelineProblem != null) {
      return HandlerResult.rejected(timelineProblem);
    }
    HandlerResult result = process(cameraId, event);
    if (result.isAccepted()) {
      afterAccepted(cameraId, event, result);
    }
    return result;
  }

  protected abstract HandlerResult process(String cameraId, ParkingEventMessage event);

  /** Gancho opcional para notificaciones en vivo. */
  protected void afterAccepted(String cameraId, ParkingEventMessage event, HandlerResult result) {
    // Por defecto no hace nada.
  }

  protected Clock clock() {
    return clock;
  }

  private String checkTimeline(ParkingEventMessage event) {
    if (event.occurredAt().isBefore(event.enteredAt())) {
      return "occurredAt es anterior a enteredAt";
    }
    if (event.occurredAt().isAfter(clock.instant().plus(MAX_CLOCK_SKEW))) {
      return "El evento está en el futuro: revise la sincronización NTP del agente";
    }
    return null;
  }
}

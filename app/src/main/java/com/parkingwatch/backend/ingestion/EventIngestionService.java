package com.parkingwatch.backend.ingestion;

import com.parkingwatch.common.contract.EventResult;
import com.parkingwatch.common.contract.ParkingEventMessage;
import com.parkingwatch.common.domain.ParkingEventType;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fachada de ingesta de eventos del agente. Procesa en orden cronológico, cada evento en su propia
 * transacción, y garantiza idempotencia: el agente reenvía los eventos guardados en su buzón local
 * tras perder conexión (RNF-5.2).
 */
@Service
public class EventIngestionService {

  private static final Logger LOG = LoggerFactory.getLogger(EventIngestionService.class);

  private final Map<ParkingEventType, ParkingEventHandler> handlers =
      new EnumMap<>(ParkingEventType.class);
  private final IngestedEventRepository ingested;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public EventIngestionService(
      List<ParkingEventHandler> handlers,
      IngestedEventRepository ingested,
      TransactionTemplate transactions,
      Clock clock) {
    handlers.forEach(handler -> this.handlers.put(handler.eventType(), handler));
    this.ingested = ingested;
    this.transactions = transactions;
    this.clock = clock;
  }

  /** Procesa un lote de eventos de una cámara. */
  public List<EventResult> ingest(String cameraId, List<ParkingEventMessage> events) {
    List<ParkingEventMessage> ordered = new ArrayList<>(events);
    ordered.sort(Comparator.comparing(ParkingEventMessage::occurredAt));
    return ordered.stream().map(event -> ingestOne(cameraId, event)).toList();
  }

  private EventResult ingestOne(String cameraId, ParkingEventMessage event) {
    try {
      HandlerResult result =
          Objects.requireNonNull(
              transactions.execute(status -> processInTransaction(cameraId, event)));
      return new EventResult(event.eventId(), result.outcome(), result.reason(), result.reportId());
    } catch (DataIntegrityViolationException e) {
      // Una carrera entre reenvíos violó una restricción única: el evento ya fue registrado.
      LOG.info("Evento {} duplicado detectado por la base de datos", event.eventId());
      return EventResult.duplicate(event.eventId(), "Evento ya procesado");
    }
  }

  private HandlerResult processInTransaction(String cameraId, ParkingEventMessage event) {
    if (ingested.existsById(event.eventId())) {
      return HandlerResult.duplicate("Evento ya procesado");
    }
    ParkingEventHandler handler = handlers.get(event.eventType());
    HandlerResult result =
        handler == null
            ? HandlerResult.rejected("Tipo de evento no soportado")
            : handler.handle(cameraId, event);
    ingested.saveAndFlush(
        new IngestedEvent(
            event.eventId(), cameraId, event.eventType(), result.outcome(), clock.instant()));
    LOG.debug("Evento {} de {}: {}", event.eventId(), cameraId, result.outcome());
    return result;
  }
}

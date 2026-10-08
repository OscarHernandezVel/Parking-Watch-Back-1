package com.parkingwatch.backend.realtime;

import com.parkingwatch.backend.shared.RealtimeEvent;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica por WebSocket los eventos del dominio (reporte nuevo o revisado, cambios de estado de
 * cámaras, eventos de zona y alertas) después de confirmarse la transacción, para que la página
 * nunca muestre datos que no se guardaron (RF-3.4, RF-7.3).
 */
@Component
public class RealtimeEventRelay {

  private final RealtimeBroadcaster broadcaster;
  private final Clock clock;

  public RealtimeEventRelay(RealtimeBroadcaster broadcaster, Clock clock) {
    this.broadcaster = broadcaster;
    this.clock = clock;
  }

  /** Envía el evento como {type, at, payload} al tópico del evento. */
  @TransactionalEventListener(fallbackExecution = true)
  public void onEvent(RealtimeEvent event) {
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("type", event.name());
    message.put("at", clock.instant());
    message.put("payload", event.payload());
    broadcaster.send(event.channel(), message);
  }
}

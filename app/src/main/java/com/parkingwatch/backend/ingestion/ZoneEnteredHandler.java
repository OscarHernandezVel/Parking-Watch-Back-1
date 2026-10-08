package com.parkingwatch.backend.ingestion;

import com.parkingwatch.backend.shared.RealtimeEvent;
import com.parkingwatch.backend.zone.ZoneRepository;
import com.parkingwatch.common.contract.ParkingEventMessage;
import com.parkingwatch.common.domain.ParkingEventType;
import java.time.Clock;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Vehículo entra a una zona: no genera reporte, solo se notifica a la vista en vivo. */
@Component
public class ZoneEnteredHandler extends ParkingEventHandler {

  private final ZoneRepository zones;
  private final ApplicationEventPublisher events;

  public ZoneEnteredHandler(Clock clock, ZoneRepository zones, ApplicationEventPublisher events) {
    super(clock);
    this.zones = zones;
    this.events = events;
  }

  @Override
  public ParkingEventType eventType() {
    return ParkingEventType.ZONE_ENTERED;
  }

  @Override
  protected HandlerResult process(String cameraId, ParkingEventMessage event) {
    boolean known =
        zones
            .findById(event.zoneId())
            .filter(zone -> zone.getCameraId().equals(cameraId))
            .isPresent();
    return known
        ? HandlerResult.accepted(null)
        : HandlerResult.rejected("Zona desconocida para la cámara");
  }

  @Override
  protected void afterAccepted(String cameraId, ParkingEventMessage event, HandlerResult result) {
    events.publishEvent(
        new RealtimeEvent(
            RealtimeEvent.cameraChannel(cameraId),
            "vehicle.entered-zone",
            Map.of(
                "zoneId", event.zoneId(),
                "trackId", event.trackId(),
                "vehicleType", event.vehicleType(),
                "enteredAt", event.enteredAt())));
  }
}

package com.parkingwatch.backend.ingestion;

import com.parkingwatch.backend.report.Report;
import com.parkingwatch.backend.report.ReportRepository;
import com.parkingwatch.backend.shared.RealtimeEvent;
import com.parkingwatch.common.contract.ParkingEventMessage;
import com.parkingwatch.common.domain.ParkingEventType;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Vehículo sale de la zona: cierra la permanencia de su reporte, si existe (RF-7.2). */
@Component
public class ZoneExitedHandler extends ParkingEventHandler {

  private final ReportRepository reports;
  private final ApplicationEventPublisher events;

  public ZoneExitedHandler(
      Clock clock, ReportRepository reports, ApplicationEventPublisher events) {
    super(clock);
    this.reports = reports;
    this.events = events;
  }

  @Override
  public ParkingEventType eventType() {
    return ParkingEventType.ZONE_EXITED;
  }

  @Override
  protected HandlerResult process(String cameraId, ParkingEventMessage event) {
    Optional<Report> report =
        reports.findByCameraIdAndZoneIdAndTrackIdAndEnteredAt(
            cameraId, event.zoneId(), event.trackId(), event.enteredAt());
    report.ifPresent(found -> found.registerExit(event.occurredAt()));
    return HandlerResult.accepted(report.map(Report::getId).orElse(null));
  }

  @Override
  protected void afterAccepted(String cameraId, ParkingEventMessage event, HandlerResult result) {
    events.publishEvent(
        new RealtimeEvent(
            RealtimeEvent.cameraChannel(cameraId),
            "vehicle.exited-zone",
            Map.of(
                "zoneId",
                event.zoneId(),
                "trackId",
                event.trackId(),
                "exitedAt",
                event.occurredAt())));
    if (result.reportId() != null) {
      events.publishEvent(
          new RealtimeEvent(
              RealtimeEvent.REPORTS, "report.updated", Map.of("id", result.reportId())));
    }
  }
}

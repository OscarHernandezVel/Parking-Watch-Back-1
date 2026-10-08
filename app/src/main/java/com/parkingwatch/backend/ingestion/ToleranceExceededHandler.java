package com.parkingwatch.backend.ingestion;

import com.parkingwatch.backend.model.ModelVersionService;
import com.parkingwatch.backend.report.Report;
import com.parkingwatch.backend.report.ReportDraft;
import com.parkingwatch.backend.report.ReportRepository;
import com.parkingwatch.backend.rules.ReportCandidate;
import com.parkingwatch.backend.rules.ReportRule;
import com.parkingwatch.backend.rules.RuleOutcome;
import com.parkingwatch.backend.shared.RealtimeEvent;
import com.parkingwatch.backend.zone.NoParkingZone;
import com.parkingwatch.backend.zone.ZoneRepository;
import com.parkingwatch.common.contract.ParkingEventMessage;
import com.parkingwatch.common.contract.PlateReading;
import com.parkingwatch.common.domain.ParkingEventType;
import com.parkingwatch.common.domain.PlateNumber;
import com.parkingwatch.common.domain.PlateStatus;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Vehículo inmóvil que supera la tolerancia: aplica el motor de reglas y crea el reporte con su
 * evidencia, la lectura de placa y la versión del modelo (RF-7.1, RF-7.3).
 */
@Component
public class ToleranceExceededHandler extends ParkingEventHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ToleranceExceededHandler.class);

  private final ZoneRepository zones;
  private final ReportRepository reports;
  private final ReportRule rules;
  private final ModelVersionService models;
  private final ApplicationEventPublisher events;

  public ToleranceExceededHandler(
      Clock clock,
      ZoneRepository zones,
      ReportRepository reports,
      ReportRule rules,
      ModelVersionService models,
      ApplicationEventPublisher events) {
    super(clock);
    this.zones = zones;
    this.reports = reports;
    this.rules = rules;
    this.models = models;
    this.events = events;
  }

  @Override
  public ParkingEventType eventType() {
    return ParkingEventType.TOLERANCE_EXCEEDED;
  }

  @Override
  protected HandlerResult process(String cameraId, ParkingEventMessage event) {
    NoParkingZone zone = zones.findById(event.zoneId()).orElse(null);
    RuleOutcome outcome = rules.check(new ReportCandidate(cameraId, event, zone));
    if (!outcome.accepted()) {
      LOG.info("Evento {} rechazado por {}: {}", event.eventId(), outcome.rule(), outcome.reason());
      return outcome.duplicate()
          ? HandlerResult.duplicate(outcome.reason())
          : HandlerResult.rejected(outcome.reason());
    }
    String modelVersion = models.resolveForReport(event.modelVersion(), cameraId);
    Report report = reports.saveAndFlush(new Report(draft(cameraId, event, modelVersion)));
    LOG.info("Reporte {} creado en la zona {} de {}", report.getId(), event.zoneId(), cameraId);
    return HandlerResult.accepted(report.getId());
  }

  @Override
  protected void afterAccepted(String cameraId, ParkingEventMessage event, HandlerResult result) {
    Map<String, Object> notification = new HashMap<>();
    notification.put("id", result.reportId());
    notification.put("cameraId", cameraId);
    notification.put("zoneId", event.zoneId());
    notification.put("vehicleType", event.vehicleType());
    notification.put("plate", event.plate().value());
    notification.put("dwellSeconds", Math.round(event.dwellSeconds()));
    events.publishEvent(new RealtimeEvent(RealtimeEvent.REPORTS, "report.created", notification));
    events.publishEvent(
        new RealtimeEvent(RealtimeEvent.cameraChannel(cameraId), "report.created", notification));
  }

  private ReportDraft draft(String cameraId, ParkingEventMessage event, String modelVersion) {
    PlateReading plate = normalizePlate(event);
    return new ReportDraft(
        UUID.randomUUID(),
        cameraId,
        event.zoneId(),
        event.trackId(),
        event.vehicleType(),
        plate.value(),
        plate.confidence() == null ? null : round2(plate.confidence()),
        plate.status(),
        round2(event.detectionConfidence()),
        event.enteredAt(),
        clock().instant(),
        event.evidence(),
        modelVersion,
        event.eventId());
  }

  /**
   * Revalida la placa: si el formato no es colombiano se descarta la lectura; si no concuerda con
   * el tipo de vehículo queda "por confirmar" (RF-11.2).
   */
  private static PlateReading normalizePlate(ParkingEventMessage event) {
    Optional<String> plate = PlateNumber.normalize(event.plate().value());
    if (plate.isEmpty()) {
      return PlateReading.notRead();
    }
    PlateStatus status =
        PlateNumber.isConsistentWith(plate.get(), event.vehicleType())
            ? event.plate().status()
            : PlateStatus.PENDING_CONFIRMATION;
    return new PlateReading(plate.get(), event.plate().confidence(), status);
  }

  private static double round2(double value) {
    return Math.round(value * 100) / 100.0;
  }
}

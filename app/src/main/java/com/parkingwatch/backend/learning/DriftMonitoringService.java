package com.parkingwatch.backend.learning;

import com.parkingwatch.backend.config.ParkingProperties;
import com.parkingwatch.backend.report.ReportRepository;
import com.parkingwatch.common.contract.DriftAlertMessage;
import com.parkingwatch.common.domain.AlertType;
import java.util.Locale;
import java.util.OptionalDouble;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monitoreo de deriva (RF-14.2). El agente calcula en el borde la deriva de confianza y brillo; el
 * backend vigila la tasa de reportes descartados por error del sistema (KPI: menos del 5 %).
 */
@Service
public class DriftMonitoringService {

  private final AlertService alerts;
  private final ReportRepository reports;
  private final ParkingProperties.Learning settings;

  public DriftMonitoringService(
      AlertService alerts, ReportRepository reports, ParkingProperties properties) {
    this.alerts = alerts;
    this.reports = reports;
    this.settings = properties.learning();
  }

  /** Registra una alerta de deriva enviada por el agente. */
  @Transactional
  public void recordEdgeAlert(String cameraId, DriftAlertMessage message) {
    alerts.raiseOnce(
        new Alert.AlertDraft(
            AlertType.valueOf(message.alertType().name()),
            cameraId,
            message.message(),
            message.observedValue(),
            message.baselineValue()));
  }

  /** Revisa la tasa de descartes; retorna vacío si la muestra aún es pequeña. */
  @Scheduled(cron = "${parking.learning.dismissal-check-cron:0 0 * * * *}")
  @Transactional
  public OptionalDouble checkDismissalRate() {
    ReportRepository.ReviewOutcomes sample =
        reports.recentReviewOutcomes(settings.dismissalSampleSize());
    if (sample.getReviewed() < settings.dismissalMinimumSample()) {
      return OptionalDouble.empty();
    }
    double rate = (double) sample.getDismissed() / sample.getReviewed();
    if (rate > settings.dismissalRateThreshold()) {
      alerts.raiseOnce(
          new Alert.AlertDraft(
              AlertType.DISMISSAL_RATE_DRIFT,
              null,
              String.format(
                  Locale.ROOT, "Tasa de descartes por error del sistema de %.1f %%", rate * 100),
              rate,
              settings.dismissalRateThreshold()));
    }
    return OptionalDouble.of(rate);
  }
}

package com.parkingwatch.backend.learning;

import com.parkingwatch.backend.shared.NotFoundException;
import com.parkingwatch.backend.shared.RealtimeEvent;
import com.parkingwatch.common.domain.AlertType;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alertas para el administrador: deriva, reentrenamiento y modelos no verificados (PB-14). */
@Service
public class AlertService {

  private static final Logger LOG = LoggerFactory.getLogger(AlertService.class);
  private static final int LIST_LIMIT = 200;

  private final AlertRepository alerts;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public AlertService(AlertRepository alerts, ApplicationEventPublisher events, Clock clock) {
    this.alerts = alerts;
    this.events = events;
    this.clock = clock;
  }

  /** Registra la alerta solo si no hay otra abierta del mismo tipo para la misma cámara. */
  @Transactional
  public Optional<AlertView> raiseOnce(Alert.AlertDraft draft) {
    if (alerts.hasOpen(draft.alertType(), draft.cameraId())) {
      return Optional.empty();
    }
    Alert alert = alerts.save(new Alert(draft, clock.instant()));
    LOG.warn("Alerta {}: {}", alert.getAlertType(), alert.getMessage());
    AlertView view = AlertView.of(alert);
    events.publishEvent(new RealtimeEvent(RealtimeEvent.ALERTS, "alert.created", view));
    return Optional.of(view);
  }

  @Transactional(readOnly = true)
  public List<AlertView> list(boolean openOnly) {
    PageRequest page = PageRequest.of(0, LIST_LIMIT);
    List<Alert> found =
        openOnly
            ? alerts.findByAcknowledgedAtIsNullOrderByCreatedAtDesc(page)
            : alerts.findAllByOrderByCreatedAtDesc(page);
    return found.stream().map(AlertView::of).toList();
  }

  /** Marca la alerta como atendida; falla si no existe o ya fue atendida. */
  @Transactional
  public void acknowledge(long id, UUID userId) {
    Alert alert = alerts.findById(id).orElseThrow(() -> new NotFoundException("Alerta", id));
    if (!alert.acknowledge(userId, clock.instant())) {
      throw new NotFoundException("Alerta abierta", id);
    }
  }

  /** Alertas de deriva abiertas (activan el reentrenamiento, RF-14.3). */
  @Transactional(readOnly = true)
  public long countOpenDriftAlerts() {
    List<AlertType> drift = Arrays.stream(AlertType.values()).filter(AlertType::isDrift).toList();
    return alerts.countByAlertTypeInAndAcknowledgedAtIsNull(drift);
  }
}

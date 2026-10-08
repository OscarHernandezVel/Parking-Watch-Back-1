package com.parkingwatch.backend.learning;

import com.parkingwatch.common.domain.AlertType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Alerta para el administrador sobre el modelo o el reentrenamiento (tabla model_alerts). */
@Entity
@Table(name = "model_alerts")
public class Alert {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "alert_type", nullable = false, length = 30)
  private AlertType alertType;

  @Column(name = "camera_id", length = 20)
  private String cameraId;

  @Column(nullable = false)
  private String message;

  @Column(name = "observed_value")
  private Double observedValue;

  @Column(name = "baseline_value")
  private Double baselineValue;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "acknowledged_at")
  private Instant acknowledgedAt;

  @Column(name = "acknowledged_by")
  private UUID acknowledgedBy;

  protected Alert() {}

  /** Crea una alerta abierta. */
  public Alert(AlertDraft draft, Instant now) {
    this.alertType = draft.alertType();
    this.cameraId = draft.cameraId();
    this.message = draft.message();
    this.observedValue = draft.observedValue();
    this.baselineValue = draft.baselineValue();
    this.createdAt = now;
  }

  /** Datos de una alerta nueva. */
  public record AlertDraft(
      AlertType alertType,
      String cameraId,
      String message,
      Double observedValue,
      Double baselineValue) {}

  /** Marca la alerta como atendida; retorna false si ya lo estaba. */
  public boolean acknowledge(UUID userId, Instant at) {
    if (acknowledgedAt != null) {
      return false;
    }
    acknowledgedAt = at;
    acknowledgedBy = userId;
    return true;
  }

  public Long getId() {
    return id;
  }

  public AlertType getAlertType() {
    return alertType;
  }

  public String getCameraId() {
    return cameraId;
  }

  public String getMessage() {
    return message;
  }

  public Double getObservedValue() {
    return observedValue;
  }

  public Double getBaselineValue() {
    return baselineValue;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getAcknowledgedAt() {
    return acknowledgedAt;
  }
}

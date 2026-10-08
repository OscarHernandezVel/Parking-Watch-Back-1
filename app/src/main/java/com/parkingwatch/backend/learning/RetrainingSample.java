package com.parkingwatch.backend.learning;

import com.parkingwatch.common.domain.DismissalReason;
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

/**
 * Muestra del dataset de reentrenamiento (RF-14.1). No tiene llave foránea al reporte: sobrevive al
 * borrado del reporte descartado a los 30 días (RNF-2.2).
 */
@Entity
@Table(name = "retraining_samples")
public class RetrainingSample {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "report_id", nullable = false, unique = true)
  private UUID reportId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private DismissalReason reason;

  @Column(name = "image_object_key", nullable = false)
  private String imageObjectKey;

  @Column(name = "label_object_key", nullable = false)
  private String labelObjectKey;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  protected RetrainingSample() {}

  /** Crea una muestra pendiente de usar en un reentrenamiento. */
  public RetrainingSample(
      UUID reportId, DismissalReason reason, String imageKey, String labelKey, Instant now) {
    this.reportId = reportId;
    this.reason = reason;
    this.imageObjectKey = imageKey;
    this.labelObjectKey = labelKey;
    this.createdAt = now;
  }

  public UUID getReportId() {
    return reportId;
  }

  public Instant getConsumedAt() {
    return consumedAt;
  }
}

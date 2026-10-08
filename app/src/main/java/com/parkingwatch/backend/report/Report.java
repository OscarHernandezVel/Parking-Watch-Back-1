package com.parkingwatch.backend.report;

import com.parkingwatch.backend.shared.ValidationException;
import com.parkingwatch.common.domain.DismissalReason;
import com.parkingwatch.common.domain.PlateStatus;
import com.parkingwatch.common.domain.ReportStatus;
import com.parkingwatch.common.domain.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Reporte de parqueo en zona no autorizada (tabla reports, PB-03 y PB-07). */
@Entity
@Table(name = "reports")
public class Report {

  @Id private UUID id;

  @Column(name = "camera_id", nullable = false, length = 20)
  private String cameraId;

  @Column(name = "zone_id", nullable = false)
  private long zoneId;

  @Column(name = "track_id", nullable = false)
  private long trackId;

  @Enumerated(EnumType.STRING)
  @Column(name = "vehicle_type", nullable = false, length = 12)
  private VehicleType vehicleType;

  @Column(length = 7)
  private String plate;

  @Column(name = "plate_confidence")
  private Double plateConfidence;

  @Enumerated(EnumType.STRING)
  @Column(name = "plate_status", nullable = false, length = 25)
  private PlateStatus plateStatus;

  @Column(name = "detection_confidence", nullable = false)
  private double detectionConfidence;

  @Column(name = "entered_at", nullable = false)
  private Instant enteredAt;

  @Column(name = "generated_at", nullable = false)
  private Instant generatedAt;

  @Column(name = "exited_at")
  private Instant exitedAt;

  @Column(name = "total_seconds")
  private Integer totalSeconds;

  @Column(name = "entry_photo_key", nullable = false)
  private String entryPhotoKey;

  @Column(name = "report_photo_key", nullable = false)
  private String reportPhotoKey;

  @Column(name = "plate_photo_key", nullable = false)
  private String platePhotoKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private ReportStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "dismissal_reason", length = 40)
  private DismissalReason dismissalReason;

  @Column(name = "model_version", nullable = false, length = 20)
  private String modelVersion;

  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "source_event_id", nullable = false, unique = true)
  private UUID sourceEventId;

  protected Report() {}

  /** Crea un reporte nuevo a partir de los datos validados por el motor de reglas. */
  public Report(ReportDraft draft) {
    this.id = draft.id();
    this.cameraId = draft.cameraId();
    this.zoneId = draft.zoneId();
    this.trackId = draft.trackId();
    this.vehicleType = draft.vehicleType();
    this.plate = draft.plate();
    this.plateConfidence = draft.plateConfidence();
    this.plateStatus = draft.plateStatus();
    this.detectionConfidence = draft.detectionConfidence();
    this.enteredAt = draft.enteredAt();
    this.generatedAt = draft.generatedAt();
    this.entryPhotoKey = draft.evidence().entryPhotoKey();
    this.reportPhotoKey = draft.evidence().reportPhotoKey();
    this.platePhotoKey = draft.evidence().platePhotoKey();
    this.modelVersion = draft.modelVersion();
    this.sourceEventId = draft.sourceEventId();
    this.status = ReportStatus.NEW;
  }

  /** Confirma el reporte (RF-3.3). */
  public void confirm(UUID reviewerId, Instant at) {
    ReportState.of(status).confirm(this, reviewerId, at);
  }

  /** Descarta el reporte con su motivo (RF-3.3). */
  public void dismiss(UUID reviewerId, Instant at, DismissalReason reason) {
    if (reason == null) {
      throw new ValidationException("Descartar un reporte exige elegir un motivo");
    }
    ReportState.of(status).dismiss(this, reviewerId, at, reason);
  }

  /** Registra la salida y el tiempo total de permanencia (RF-7.2). Idempotente. */
  public boolean registerExit(Instant at) {
    if (exitedAt != null) {
      return false;
    }
    if (at.isBefore(enteredAt)) {
      throw new ValidationException("La hora de salida es anterior a la hora de entrada");
    }
    exitedAt = at;
    totalSeconds = (int) Duration.between(enteredAt, at).toSeconds();
    return true;
  }

  /** Uso exclusivo de los estados del patrón State. */
  void applyReview(ReportStatus newStatus, DismissalReason reason, UUID reviewerId, Instant at) {
    this.status = newStatus;
    this.dismissalReason = reason;
    this.reviewedBy = reviewerId;
    this.reviewedAt = at;
  }

  /** Segundos detenido: total si ya salió, o hasta la generación del reporte. */
  public long dwellSeconds() {
    return totalSeconds != null
        ? totalSeconds
        : Math.max(0, Duration.between(enteredAt, generatedAt).toSeconds());
  }

  public UUID getId() {
    return id;
  }

  public String getCameraId() {
    return cameraId;
  }

  public long getZoneId() {
    return zoneId;
  }

  public long getTrackId() {
    return trackId;
  }

  public VehicleType getVehicleType() {
    return vehicleType;
  }

  public String getPlate() {
    return plate;
  }

  public Double getPlateConfidence() {
    return plateConfidence;
  }

  public PlateStatus getPlateStatus() {
    return plateStatus;
  }

  public double getDetectionConfidence() {
    return detectionConfidence;
  }

  public Instant getEnteredAt() {
    return enteredAt;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public Instant getExitedAt() {
    return exitedAt;
  }

  public Integer getTotalSeconds() {
    return totalSeconds;
  }

  public String getEntryPhotoKey() {
    return entryPhotoKey;
  }

  public String getReportPhotoKey() {
    return reportPhotoKey;
  }

  public String getPlatePhotoKey() {
    return platePhotoKey;
  }

  public ReportStatus getStatus() {
    return status;
  }

  public DismissalReason getDismissalReason() {
    return dismissalReason;
  }

  public String getModelVersion() {
    return modelVersion;
  }

  public UUID getReviewedBy() {
    return reviewedBy;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public UUID getSourceEventId() {
    return sourceEventId;
  }
}

package com.parkingwatch.backend.report;

import com.parkingwatch.common.domain.DismissalReason;
import com.parkingwatch.common.domain.PlateStatus;
import com.parkingwatch.common.domain.ReportStatus;
import com.parkingwatch.common.domain.VehicleType;
import java.time.Instant;
import java.util.UUID;

/** Reporte tal como lo lista la página web (RF-3.1); no expone las rutas de los archivos. */
public record ReportView(
    UUID id,
    String cameraId,
    long zoneId,
    long trackId,
    VehicleType vehicleType,
    String plate,
    Double plateConfidence,
    PlateStatus plateStatus,
    double detectionConfidence,
    Instant enteredAt,
    Instant generatedAt,
    Instant exitedAt,
    Integer totalSeconds,
    ReportStatus status,
    DismissalReason dismissalReason,
    String modelVersion,
    UUID reviewedBy,
    Instant reviewedAt) {

  /** Construye la vista a partir de la entidad. */
  public static ReportView of(Report report) {
    return new ReportView(
        report.getId(),
        report.getCameraId(),
        report.getZoneId(),
        report.getTrackId(),
        report.getVehicleType(),
        report.getPlate(),
        report.getPlateConfidence(),
        report.getPlateStatus(),
        report.getDetectionConfidence(),
        report.getEnteredAt(),
        report.getGeneratedAt(),
        report.getExitedAt(),
        report.getTotalSeconds(),
        report.getStatus(),
        report.getDismissalReason(),
        report.getModelVersion(),
        report.getReviewedBy(),
        report.getReviewedAt());
  }
}

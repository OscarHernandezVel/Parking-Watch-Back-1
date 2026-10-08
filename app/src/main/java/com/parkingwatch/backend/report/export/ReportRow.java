package com.parkingwatch.backend.report.export;

import com.parkingwatch.common.domain.ReportStatus;
import com.parkingwatch.common.domain.VehicleType;
import java.time.Instant;

/** Fila plana de un reporte lista para exportarse. */
public record ReportRow(
    Instant generatedAt,
    String cameraId,
    String zoneName,
    VehicleType vehicleType,
    String plate,
    long dwellSeconds,
    ReportStatus status) {}

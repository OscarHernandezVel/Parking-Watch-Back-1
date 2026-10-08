package com.parkingwatch.backend.report;

import com.parkingwatch.common.contract.EvidenceKeys;
import com.parkingwatch.common.domain.PlateStatus;
import com.parkingwatch.common.domain.VehicleType;
import java.time.Instant;
import java.util.UUID;

/** Datos validados para crear un reporte. */
public record ReportDraft(
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
    EvidenceKeys evidence,
    String modelVersion,
    UUID sourceEventId) {}

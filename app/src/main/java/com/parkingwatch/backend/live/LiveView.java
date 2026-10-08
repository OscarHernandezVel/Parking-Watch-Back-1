package com.parkingwatch.backend.live;

import com.parkingwatch.common.contract.AnalyticsSnapshot;
import com.parkingwatch.common.contract.DetectionSnapshot;
import java.time.Instant;

/**
 * Vista en vivo de la cámara (PB-02). Si la cámara se desconecta, la página muestra "Sin conexión"
 * y la hora de la última señal (Historia de Usuario 2).
 */
public record LiveView(
    String cameraId,
    boolean connected,
    Instant lastSignalAt,
    DetectionSnapshot detections,
    Instant detectionsAt,
    AnalyticsSnapshot analytics,
    Instant analyticsAt) {}

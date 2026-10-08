package com.parkingwatch.backend.rules;

import com.parkingwatch.backend.zone.NoParkingZone;
import com.parkingwatch.common.contract.ParkingEventMessage;

/**
 * Datos que evalúa la cadena de reglas antes de crear un reporte.
 *
 * @param cameraId cámara autenticada que envió el evento
 * @param event evento TOLERANCE_EXCEEDED
 * @param zone zona del evento, o null si no existe
 */
public record ReportCandidate(String cameraId, ParkingEventMessage event, NoParkingZone zone) {}

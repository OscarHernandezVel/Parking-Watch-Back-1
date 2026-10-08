package com.parkingwatch.backend.statistics;

import java.time.Instant;

/** Punto de la serie de tendencias de una zona (RF-12.2). */
public record StatisticsPoint(
    Instant bucket,
    long zoneId,
    long vehiclesDetected,
    double occupancyPct,
    Double avgDwellSeconds,
    double fps,
    double avgConfidence) {}

package com.parkingwatch.backend.report.export;

import java.time.Instant;

/** Datos del encabezado del archivo exportado. */
public record ExportMetadata(String title, Instant generatedAt, String filterDescription) {}

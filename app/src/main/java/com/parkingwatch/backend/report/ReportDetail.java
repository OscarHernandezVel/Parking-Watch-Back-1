package com.parkingwatch.backend.report;

import com.parkingwatch.common.domain.ZoneType;
import java.util.UUID;

/**
 * Detalle del reporte con su evidencia (RF-3.2). Las fotos se descargan de la API con la sesión del
 * funcionario (nunca son públicas); aquí van sus rutas relativas a la URL del backend.
 */
public record ReportDetail(ReportView report, ZoneSummary zone, EvidenceUrls evidenceUrls) {

  /** Zona donde ocurrió. */
  public record ZoneSummary(long id, String name, ZoneType zoneType) {}

  /** Rutas de las tres fotos: al entrar, al cumplirse la tolerancia y recorte de la placa. */
  public record EvidenceUrls(String entry, String report, String plate) {

    /** Rutas de la API para un reporte. */
    public static EvidenceUrls forReport(UUID id) {
      String base = "/api/v1/reports/" + id + "/evidence/";
      return new EvidenceUrls(base + "entry", base + "report", base + "plate");
    }
  }
}

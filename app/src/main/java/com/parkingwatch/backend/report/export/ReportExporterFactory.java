package com.parkingwatch.backend.report.export;

import com.parkingwatch.backend.shared.ValidationException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Patrón Factory: entrega el exportador del formato pedido (?format=pdf|xlsx). */
@Component
public class ReportExporterFactory {

  private final Map<ExportFormat, ReportExporter<?>> exporters = new EnumMap<>(ExportFormat.class);

  public ReportExporterFactory(List<ReportExporter<?>> available) {
    available.forEach(exporter -> exporters.put(exporter.format(), exporter));
  }

  /** Exportador del formato o error si no está disponible. */
  public ReportExporter<?> create(ExportFormat format) {
    ReportExporter<?> exporter = exporters.get(format);
    if (exporter == null) {
      throw new ValidationException("Formato de exportación no soportado: " + format);
    }
    return exporter;
  }
}

package com.parkingwatch.backend.report.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Patrón Template Method: el algoritmo de exportación es fijo (iniciar documento, encabezado, filas
 * y cierre) y cada formato implementa los pasos. Cada subclase es además una Strategy que
 * selecciona {@link ReportExporterFactory}.
 *
 * @param <D> documento en construcción propio de cada formato
 */
public abstract class ReportExporter<D> {

  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss").withZone(ZoneOffset.UTC);

  /** Formato que produce este exportador. */
  public abstract ExportFormat format();

  /** Genera el archivo con todas las filas. */
  public final ExportFile export(List<ReportRow> rows, ExportMetadata metadata) {
    try {
      D document = begin(metadata);
      writeHeader(document, ExportColumns.ALL.stream().map(ExportColumns.Column::header).toList());
      for (ReportRow row : rows) {
        writeRow(document, ExportColumns.ALL.stream().map(c -> c.value().apply(row)).toList());
      }
      byte[] body = finish(document, rows.size());
      String filename =
          "reportes-" + STAMP.format(metadata.generatedAt()) + "." + format().extension();
      return new ExportFile(filename, format().contentType(), body);
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo generar el archivo de exportación", e);
    }
  }

  protected abstract D begin(ExportMetadata metadata) throws IOException;

  protected abstract void writeHeader(D document, List<String> headers);

  protected abstract void writeRow(D document, List<String> cells);

  protected abstract byte[] finish(D document, int rowCount) throws IOException;
}

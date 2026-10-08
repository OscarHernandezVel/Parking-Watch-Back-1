package com.parkingwatch.backend.report.export;

import com.parkingwatch.common.domain.ReportStatus;
import com.parkingwatch.common.domain.VehicleType;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Columnas comunes a todos los formatos, con textos en lenguaje claro para los funcionarios. */
public final class ExportColumns {

  /** Columna con su encabezado, ancho relativo y extractor de valor. */
  public record Column(String header, int width, Function<ReportRow, String> value) {}

  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("America/Bogota"));

  private static final Map<VehicleType, String> VEHICLES =
      Map.of(
          VehicleType.CAR, "Carro",
          VehicleType.MOTORCYCLE, "Moto",
          VehicleType.BUS, "Bus",
          VehicleType.TRUCK, "Camión");

  private static final Map<ReportStatus, String> STATUSES =
      Map.of(
          ReportStatus.NEW, "Nuevo",
          ReportStatus.CONFIRMED, "Confirmado",
          ReportStatus.DISMISSED, "Descartado");

  public static final List<Column> ALL =
      List.of(
          new Column("Fecha y hora", 20, row -> formatDateTime(row.generatedAt())),
          new Column("Cámara", 14, ReportRow::cameraId),
          new Column("Zona", 20, ReportRow::zoneName),
          new Column("Vehículo", 10, row -> VEHICLES.get(row.vehicleType())),
          new Column("Placa", 10, row -> row.plate() == null ? "Sin lectura" : row.plate()),
          new Column("Tiempo detenido", 14, row -> formatDuration(row.dwellSeconds())),
          new Column("Estado", 12, row -> STATUSES.get(row.status())));

  private ExportColumns() {}

  /** Fecha y hora en la zona horaria de Colombia. */
  public static String formatDateTime(Instant instant) {
    return DATE_TIME.format(instant);
  }

  /** Duración legible: "2 min 5 s" o "42 s". */
  public static String formatDuration(long totalSeconds) {
    long minutes = totalSeconds / 60;
    long seconds = totalSeconds % 60;
    return minutes > 0 ? minutes + " min " + seconds + " s" : seconds + " s";
  }
}

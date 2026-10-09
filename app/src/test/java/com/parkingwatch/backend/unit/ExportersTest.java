package com.parkingwatch.backend.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parkingwatch.backend.report.export.ExcelReportExporter;
import com.parkingwatch.backend.report.export.ExportColumns;
import com.parkingwatch.backend.report.export.ExportFile;
import com.parkingwatch.backend.report.export.ExportFormat;
import com.parkingwatch.backend.report.export.ExportMetadata;
import com.parkingwatch.backend.report.export.PdfReportExporter;
import com.parkingwatch.backend.report.export.ReportExporterFactory;
import com.parkingwatch.backend.report.export.ReportRow;
import com.parkingwatch.backend.shared.ValidationException;
import com.parkingwatch.common.domain.ReportStatus;
import com.parkingwatch.common.domain.VehicleType;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** Exportación de reportes (RF-3.4) con Template Method y Factory. */
class ExportersTest {

  private static final Instant GENERATED = Instant.parse("2026-10-01T15:00:00Z");
  private static final List<ReportRow> ROWS =
      IntStream.range(0, 80)
          .mapToObj(
              i ->
                  new ReportRow(
                      GENERATED,
                      "CAM-MAQ-01",
                      "Zona amarilla",
                      i % 2 == 0 ? VehicleType.CAR : VehicleType.MOTORCYCLE,
                      i == 0 ? null : "ABC123",
                      75,
                      ReportStatus.NEW))
          .toList();
  private static final ExportMetadata META =
      new ExportMetadata("Reportes", GENERATED, "Sin filtros");

  @Test
  void pdfHasSeveralPagesForLongLists() throws Exception {
    ExportFile file = new PdfReportExporter().export(ROWS, META);
    byte[] body = file.body();
    assertThat(file.filename()).isEqualTo("reportes-2026-10-01T15-00-00.pdf");
    assertThat(new String(body, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    assertThat(new com.lowagie.text.pdf.PdfReader(body).getNumberOfPages()).isGreaterThan(1);
  }

  @Test
  void excelHasHeadersAndOneRowPerReport() throws Exception {
    ExportFile file = new ExcelReportExporter().export(ROWS, META);
    assertThat(file.contentType()).contains("spreadsheetml");
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.body()))) {
      var sheet = workbook.getSheet("Reportes");
      assertThat(sheet.getLastRowNum()).isEqualTo(80);
      assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("Placa");
      assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("Sin lectura");
      assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("Moto");
      assertThat(sheet.getRow(1).getCell(5).getStringCellValue()).isEqualTo("1 min 15 s");
    }
  }

  @Test
  void factorySelectsTheExporterByFormat() {
    ReportExporterFactory factory =
        new ReportExporterFactory(List.of(new PdfReportExporter(), new ExcelReportExporter()));
    assertThat(factory.create(ExportFormat.PDF)).isInstanceOf(PdfReportExporter.class);
    assertThatThrownBy(() -> new ReportExporterFactory(List.of()).create(ExportFormat.XLSX))
        .isInstanceOf(ValidationException.class);
    assertThat(ExportColumns.formatDuration(42)).isEqualTo("42 s");
    assertThat(ExportColumns.formatDateTime(GENERATED)).isEqualTo("01/10/2026 10:00:00");
  }
}

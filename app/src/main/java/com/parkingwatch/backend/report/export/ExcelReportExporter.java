package com.parkingwatch.backend.report.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Exportación a Excel con Apache POI (RF-3.4): encabezado fijo y filtro automático. */
@Component
public class ExcelReportExporter extends ReportExporter<ExcelReportExporter.Workbook> {

  private static final int CHARACTER_WIDTH = 256;

  /** Libro en construcción. */
  public static final class Workbook {
    private final XSSFWorkbook workbook = new XSSFWorkbook();
    private final Sheet sheet = workbook.createSheet("Reportes");
    private int nextRow;
  }

  @Override
  public ExportFormat format() {
    return ExportFormat.XLSX;
  }

  @Override
  protected Workbook begin(ExportMetadata metadata) {
    Workbook book = new Workbook();
    book.workbook.getProperties().getCoreProperties().setTitle(metadata.title());
    for (int i = 0; i < ExportColumns.ALL.size(); i++) {
      book.sheet.setColumnWidth(i, (ExportColumns.ALL.get(i).width() + 4) * CHARACTER_WIDTH);
    }
    book.sheet.createFreezePane(0, 1);
    return book;
  }

  @Override
  protected void writeHeader(Workbook book, List<String> headers) {
    XSSFFont bold = book.workbook.createFont();
    bold.setBold(true);
    CellStyle style = book.workbook.createCellStyle();
    style.setFont(bold);
    Row row = writeCells(book, headers);
    row.forEach(cell -> cell.setCellStyle(style));
    book.sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.size() - 1));
  }

  @Override
  protected void writeRow(Workbook book, List<String> cells) {
    writeCells(book, cells);
  }

  @Override
  protected byte[] finish(Workbook book, int rowCount) throws IOException {
    try (XSSFWorkbook workbook = book.workbook;
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      workbook.write(output);
      return output.toByteArray();
    }
  }

  private static Row writeCells(Workbook book, List<String> values) {
    Row row = book.sheet.createRow(book.nextRow++);
    for (int i = 0; i < values.size(); i++) {
      row.createCell(i).setCellValue(values.get(i));
    }
    return row;
  }
}

package com.parkingwatch.backend.report.export;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.springframework.stereotype.Component;

/** Exportación a PDF con OpenPDF (RF-3.4): tabla horizontal con encabezado repetido. */
@Component
public class PdfReportExporter extends ReportExporter<PdfReportExporter.PdfDocument> {

  private static final Font TITLE = new Font(Font.HELVETICA, 14, Font.BOLD);
  private static final Font HEADER = new Font(Font.HELVETICA, 9, Font.BOLD);
  private static final Font CELL = new Font(Font.HELVETICA, 9, Font.NORMAL);

  /** Documento en construcción. */
  public static final class PdfDocument {
    private final Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final PdfPTable table;

    PdfDocument() {
      int[] widths = ExportColumns.ALL.stream().mapToInt(ExportColumns.Column::width).toArray();
      table = new PdfPTable(widths.length);
      table.setWidthPercentage(100);
      table.setWidths(widths);
      table.setHeaderRows(1);
    }
  }

  @Override
  public ExportFormat format() {
    return ExportFormat.PDF;
  }

  @Override
  protected PdfDocument begin(ExportMetadata metadata) {
    PdfDocument pdf = new PdfDocument();
    PdfWriter.getInstance(pdf.document, pdf.output);
    pdf.document.addTitle(metadata.title());
    pdf.document.open();
    pdf.document.add(new Paragraph(metadata.title(), TITLE));
    pdf.document.add(
        new Paragraph(
            "Generado: "
                + ExportColumns.formatDateTime(metadata.generatedAt())
                + " · "
                + metadata.filterDescription(),
            CELL));
    pdf.document.add(new Paragraph(" "));
    return pdf;
  }

  @Override
  protected void writeHeader(PdfDocument pdf, List<String> headers) {
    headers.forEach(header -> pdf.table.addCell(cell(header, HEADER)));
  }

  @Override
  protected void writeRow(PdfDocument pdf, List<String> cells) {
    cells.forEach(value -> pdf.table.addCell(cell(value, CELL)));
  }

  @Override
  protected byte[] finish(PdfDocument pdf, int rowCount) {
    pdf.document.add(pdf.table);
    pdf.document.add(new Paragraph("Total de reportes: " + rowCount, CELL));
    pdf.document.close();
    return pdf.output.toByteArray();
  }

  private static PdfPCell cell(String text, Font font) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setHorizontalAlignment(Element.ALIGN_LEFT);
    cell.setPadding(4);
    return cell;
  }
}

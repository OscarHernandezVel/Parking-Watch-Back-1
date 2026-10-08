package com.parkingwatch.backend.report.export;

/** Formatos de exportación de la lista de reportes (RF-3.4). */
public enum ExportFormat {
  PDF("pdf", "application/pdf"),
  XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final String extension;
  private final String contentType;

  ExportFormat(String extension, String contentType) {
    this.extension = extension;
    this.contentType = contentType;
  }

  public String extension() {
    return extension;
  }

  public String contentType() {
    return contentType;
  }
}

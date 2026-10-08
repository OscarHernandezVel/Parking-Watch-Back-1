package com.parkingwatch.backend.report.export;

/** Archivo generado: nombre, tipo de contenido y bytes. */
public record ExportFile(String filename, String contentType, byte[] body) {

  /** Copia defensiva del contenido. */
  public ExportFile {
    body = body.clone();
  }

  @Override
  public byte[] body() {
    return body.clone();
  }
}

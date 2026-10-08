package com.parkingwatch.backend.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.parkingwatch.common.domain.DismissalReason;
import com.parkingwatch.common.domain.ReportStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Revisión de un reporte: {"status": "CONFIRMED"} o {"status": "DISMISSED", "dismissalReason":
 * "FALSE_POSITIVE"}. La placa corregida alimenta el reentrenamiento del OCR.
 */
public record ReviewRequest(
    @NotNull ReportStatus status,
    DismissalReason dismissalReason,
    @Size(max = 10) String correctedPlate) {

  /** Solo se puede confirmar o descartar, y descartar exige motivo (HU-3). */
  @JsonIgnore
  @AssertTrue(message = "Descartar un reporte exige un motivo y no se puede volver a NEW")
  public boolean isConsistent() {
    return status == ReportStatus.CONFIRMED
        || (status == ReportStatus.DISMISSED && dismissalReason != null);
  }
}

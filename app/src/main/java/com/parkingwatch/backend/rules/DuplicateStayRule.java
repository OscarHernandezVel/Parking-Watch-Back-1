package com.parkingwatch.backend.rules;

import com.parkingwatch.backend.report.ReportRepository;

/** Un solo reporte por vehículo y por permanencia, aunque siga detenido (RF-7.2). */
public class DuplicateStayRule extends ReportRule {

  private final ReportRepository reports;

  public DuplicateStayRule(ReportRepository reports) {
    this.reports = reports;
  }

  @Override
  protected RuleOutcome evaluate(ReportCandidate candidate) {
    boolean exists =
        reports
            .findByCameraIdAndZoneIdAndTrackIdAndEnteredAt(
                candidate.cameraId(),
                candidate.event().zoneId(),
                candidate.event().trackId(),
                candidate.event().enteredAt())
            .isPresent();
    return exists
        ? RuleOutcome.duplicate(
            getClass().getSimpleName(), "Ya existe un reporte para esta permanencia")
        : RuleOutcome.ok();
  }
}

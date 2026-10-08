package com.parkingwatch.backend.rules;

/** La zona debe existir, estar activa y pertenecer a la cámara que envía el evento. */
public class ZoneBelongsToCameraRule extends ReportRule {

  @Override
  protected RuleOutcome evaluate(ReportCandidate candidate) {
    boolean valid =
        candidate.zone() != null
            && candidate.zone().isActive()
            && candidate.zone().getCameraId().equals(candidate.cameraId());
    return valid
        ? RuleOutcome.ok()
        : reject("La zona no existe, está inactiva o no pertenece a la cámara");
  }
}

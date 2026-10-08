package com.parkingwatch.backend.rules;

import java.util.Locale;

/** Descarta detecciones con confianza inferior al mínimo configurado. */
public class MinimumConfidenceRule extends ReportRule {

  private final double minimum;

  public MinimumConfidenceRule(double minimum) {
    this.minimum = minimum;
  }

  @Override
  protected RuleOutcome evaluate(ReportCandidate candidate) {
    double confidence = candidate.event().detectionConfidence();
    if (confidence < minimum) {
      return reject(
          String.format(Locale.ROOT, "Confianza %.2f menor al mínimo %.2f", confidence, minimum));
    }
    return RuleOutcome.ok();
  }
}

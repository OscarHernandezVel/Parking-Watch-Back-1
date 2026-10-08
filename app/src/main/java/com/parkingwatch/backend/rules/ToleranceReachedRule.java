package com.parkingwatch.backend.rules;

import java.time.Duration;
import java.util.Locale;

/**
 * El backend es la autoridad sobre la tolerancia (RF-7.1): el agente pudo operar con una
 * configuración desactualizada, así que se valida contra la tolerancia vigente de la zona.
 */
public class ToleranceReachedRule extends ReportRule {

  private static final double MARGIN_SECONDS = 0.5;

  @Override
  protected RuleOutcome evaluate(ReportCandidate candidate) {
    double elapsed =
        Duration.between(candidate.event().enteredAt(), candidate.event().occurredAt()).toMillis()
            / 1000.0;
    double dwell = Math.min(candidate.event().dwellSeconds(), elapsed);
    int tolerance = candidate.zone().getToleranceSeconds();
    if (dwell + MARGIN_SECONDS < tolerance) {
      return reject(
          String.format(
              Locale.ROOT,
              "Permanencia de %.1f s menor a la tolerancia de %d s",
              dwell,
              tolerance));
    }
    return RuleOutcome.ok();
  }
}

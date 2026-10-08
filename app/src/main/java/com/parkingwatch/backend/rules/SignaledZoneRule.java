package com.parkingwatch.backend.rules;

/**
 * Sin señalización verificada no se generan reportes (RF-4.2, RNF-3.1; artículo 112 de la Ley 769
 * de 2002, modificado por la Ley 2252 de 2022).
 */
public class SignaledZoneRule extends ReportRule {

  @Override
  protected RuleOutcome evaluate(ReportCandidate candidate) {
    return candidate.zone().isSignaled()
        ? RuleOutcome.ok()
        : reject("La zona no tiene señalización verificada");
  }
}

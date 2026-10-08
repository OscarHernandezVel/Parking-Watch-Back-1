package com.parkingwatch.backend.rules;

/** Resultado de evaluar las reglas: aceptado, o rechazado por una regla con su motivo. */
public record RuleOutcome(boolean accepted, boolean duplicate, String rule, String reason) {

  private static final RuleOutcome ACCEPTED = new RuleOutcome(true, false, null, null);

  public static RuleOutcome ok() {
    return ACCEPTED;
  }

  public static RuleOutcome rejected(String rule, String reason) {
    return new RuleOutcome(false, false, rule, reason);
  }

  public static RuleOutcome duplicate(String rule, String reason) {
    return new RuleOutcome(false, true, rule, reason);
  }
}

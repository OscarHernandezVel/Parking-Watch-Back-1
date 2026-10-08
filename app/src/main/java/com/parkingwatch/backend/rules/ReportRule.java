package com.parkingwatch.backend.rules;

import java.util.List;

/**
 * Patrón Chain of Responsibility: cada regla evalúa una condición y, si se cumple, delega en la
 * siguiente. La primera regla que rechaza detiene la cadena. Agregar una regla nueva no modifica
 * las existentes (principio abierto/cerrado).
 */
public abstract class ReportRule {

  private ReportRule next;

  /** Enlaza la siguiente regla y la retorna para encadenar con fluidez. */
  public ReportRule linkWith(ReportRule nextRule) {
    this.next = nextRule;
    return nextRule;
  }

  /** Evalúa esta regla y, si acepta, las siguientes. */
  public RuleOutcome check(ReportCandidate candidate) {
    RuleOutcome outcome = evaluate(candidate);
    if (!outcome.accepted() || next == null) {
      return outcome;
    }
    return next.check(candidate);
  }

  protected abstract RuleOutcome evaluate(ReportCandidate candidate);

  protected RuleOutcome reject(String reason) {
    return RuleOutcome.rejected(getClass().getSimpleName(), reason);
  }

  /** Construye la cadena a partir de una lista ordenada y retorna la primera regla. */
  public static ReportRule chainOf(List<? extends ReportRule> rules) {
    if (rules.isEmpty()) {
      throw new IllegalArgumentException("La cadena de reglas no puede estar vacía");
    }
    ReportRule head = rules.get(0);
    ReportRule current = head;
    for (ReportRule rule : rules.subList(1, rules.size())) {
      current = current.linkWith(rule);
    }
    return head;
  }
}

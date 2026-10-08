package com.parkingwatch.backend.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Patrón Specification: metas mínimas para desplegar un modelo (RF-13.2): mAP@0,5 >= 0,90;
 * precisión >= 0,95; exhaustividad >= 0,90; exactitud de placas >= 0,90.
 */
@Component
public class ModelQualityGate {

  /** Resultado de la evaluación con las métricas que fallaron. */
  public record Result(boolean passed, List<String> failures) {

    /** Copia defensiva de la lista. */
    public Result {
      failures = List.copyOf(failures);
    }
  }

  /** Evalúa las métricas contra las metas. */
  public Result evaluate(ModelMetrics metrics) {
    List<String> failures = new ArrayList<>();
    check(failures, "mAP@0.5", metrics.map50(), 0.90);
    check(failures, "precisión", metrics.precision(), 0.95);
    check(failures, "recall", metrics.recall(), 0.90);
    check(failures, "exactitud de placas", metrics.plateAccuracy(), 0.90);
    return new Result(failures.isEmpty(), failures);
  }

  /**
   * Un candidato reemplaza a la versión vigente solo si la supera en el mismo conjunto de prueba
   * (RF-14.3): mAP no menor y F1 estrictamente mayor.
   */
  public boolean outperforms(ModelMetrics candidate, ModelMetrics current) {
    return candidate.map50() >= current.map50() && candidate.f1() > current.f1();
  }

  private static void check(List<String> failures, String label, double value, double minimum) {
    if (value < minimum) {
      failures.add(String.format(Locale.ROOT, "%s = %.3f < %.2f", label, value, minimum));
    }
  }
}

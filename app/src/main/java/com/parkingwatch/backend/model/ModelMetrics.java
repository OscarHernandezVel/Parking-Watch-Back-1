package com.parkingwatch.backend.model;

/** Métricas de un modelo sobre el conjunto de prueba (RF-13.2). */
public record ModelMetrics(double map50, double precision, double recall, double plateAccuracy) {

  /** F1 entre precisión y exhaustividad. */
  public double f1() {
    double sum = precision + recall;
    return sum == 0 ? 0 : 2 * precision * recall / sum;
  }
}

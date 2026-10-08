package com.parkingwatch.backend.model;

import java.time.Instant;

/** Versión del modelo con sus métricas, sus archivos ONNX y el resultado del quality gate. */
public record ModelVersionView(
    String version,
    Instant trainedAt,
    ModelMetrics metrics,
    boolean verified,
    boolean active,
    String detectorSha256,
    String plateReaderSha256,
    ModelQualityGate.Result qualityGate) {

  static ModelVersionView of(ModelVersion model, ModelQualityGate gate) {
    return new ModelVersionView(
        model.getVersion(),
        model.getTrainedAt(),
        model.metrics(),
        model.isVerified(),
        model.isActive(),
        model.getDetectorSha256(),
        model.getPlateReaderSha256(),
        gate.evaluate(model.metrics()));
  }
}

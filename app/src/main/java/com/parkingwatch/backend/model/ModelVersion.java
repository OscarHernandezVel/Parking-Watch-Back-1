package com.parkingwatch.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Versión del modelo de IA con sus métricas (base de datos) y sus archivos ONNX en el disco
 * persistente con su huella SHA-256 (PB-09, PB-16).
 */
@Entity
@Table(name = "model_versions")
public class ModelVersion {

  @Id
  @Column(length = 20)
  private String version;

  @Column(name = "trained_at", nullable = false)
  private Instant trainedAt;

  @Column(nullable = false)
  private double map50;

  @Column(name = "precision_score", nullable = false)
  private double precision;

  @Column(nullable = false)
  private double recall;

  @Column(name = "plate_accuracy", nullable = false)
  private double plateAccuracy;

  @Column(name = "detector_object_key")
  private String detectorObjectKey;

  @Column(name = "detector_sha256", length = 64)
  private String detectorSha256;

  @Column(name = "plate_reader_object_key")
  private String plateReaderObjectKey;

  @Column(name = "plate_reader_sha256", length = 64)
  private String plateReaderSha256;

  @Column(nullable = false)
  private boolean verified;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "registered_at", nullable = false)
  private Instant registeredAt;

  protected ModelVersion() {}

  /** Registra una versión inactiva. */
  public ModelVersion(ModelRegistration registration, boolean verified, Instant now) {
    this.version = registration.version();
    this.trainedAt = registration.trainedAt();
    this.map50 = registration.metrics().map50();
    this.precision = registration.metrics().precision();
    this.recall = registration.metrics().recall();
    this.plateAccuracy = registration.metrics().plateAccuracy();
    this.detectorObjectKey = registration.detectorObjectKey();
    this.detectorSha256 = registration.detectorSha256();
    this.plateReaderObjectKey = registration.plateReaderObjectKey();
    this.plateReaderSha256 = registration.plateReaderSha256();
    this.verified = verified;
    this.registeredAt = now;
  }

  /** Datos de registro de una versión entrenada. */
  public record ModelRegistration(
      String version,
      Instant trainedAt,
      ModelMetrics metrics,
      String detectorObjectKey,
      String detectorSha256,
      String plateReaderObjectKey,
      String plateReaderSha256) {}

  public ModelMetrics metrics() {
    return new ModelMetrics(map50, precision, recall, plateAccuracy);
  }

  void setActive(boolean active) {
    this.active = active;
  }

  /** Asocia el archivo ONNX del detector o del lector de placas con su huella. */
  void attachArtifact(ModelArtifactKind kind, String key, String sha256) {
    if (kind == ModelArtifactKind.DETECTOR) {
      this.detectorObjectKey = key;
      this.detectorSha256 = sha256;
    } else {
      this.plateReaderObjectKey = key;
      this.plateReaderSha256 = sha256;
    }
  }

  public String getVersion() {
    return version;
  }

  public Instant getTrainedAt() {
    return trainedAt;
  }

  public String getDetectorObjectKey() {
    return detectorObjectKey;
  }

  public String getDetectorSha256() {
    return detectorSha256;
  }

  public String getPlateReaderObjectKey() {
    return plateReaderObjectKey;
  }

  public String getPlateReaderSha256() {
    return plateReaderSha256;
  }

  public boolean isVerified() {
    return verified;
  }

  public boolean isActive() {
    return active;
  }
}

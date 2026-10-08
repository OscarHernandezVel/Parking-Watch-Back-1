package com.parkingwatch.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Historial de activaciones: permite volver a la versión anterior (RF-13.3). */
@Entity
@Table(name = "model_activations")
public class ModelActivation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String version;

  @Column(name = "activated_by")
  private UUID activatedBy;

  @Column(name = "activated_at", nullable = false)
  private Instant activatedAt;

  protected ModelActivation() {}

  /** Registra la activación de una versión. */
  public ModelActivation(String version, UUID activatedBy, Instant activatedAt) {
    this.version = version;
    this.activatedBy = activatedBy;
    this.activatedAt = activatedAt;
  }

  public String getVersion() {
    return version;
  }
}

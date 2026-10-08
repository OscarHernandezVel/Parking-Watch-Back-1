package com.parkingwatch.backend.camera;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/** Cámara de vigilancia; en la primera fase, la cámara de la maqueta (PB-01, PB-06). */
@Entity
@Table(name = "cameras")
public class Camera {

  @Id
  @Column(length = 20)
  private String id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 100)
  private String address;

  @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
  private Point location;

  @Column(name = "coverage_area", columnDefinition = "geometry(Polygon,4326)")
  private Polygon coverageArea;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private CameraStatus status;

  @Column(name = "last_signal_at")
  private Instant lastSignalAt;

  @Column(name = "frame_width", nullable = false)
  private int frameWidth;

  @Column(name = "frame_height", nullable = false)
  private int frameHeight;

  @Column(name = "device_token_hash", length = 64)
  private String deviceTokenHash;

  @Column(name = "config_version", nullable = false)
  private long configVersion;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Camera() {}

  /** Registra una cámara nueva, sin conexión hasta recibir la primera señal de vida. */
  public Camera(CameraRegistration registration, Instant now) {
    this.id = registration.id();
    this.name = registration.name();
    this.address = registration.address();
    this.location = registration.location();
    this.coverageArea = registration.coverageArea();
    this.frameWidth = registration.frameWidth();
    this.frameHeight = registration.frameHeight();
    this.deviceTokenHash = registration.deviceTokenHash();
    this.status = CameraStatus.OFFLINE;
    this.configVersion = 1;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** Datos para registrar una cámara. */
  public record CameraRegistration(
      String id,
      String name,
      String address,
      Point location,
      Polygon coverageArea,
      int frameWidth,
      int frameHeight,
      String deviceTokenHash) {}

  /** Registra la señal de vida y retorna la hora de la señal anterior (puede ser null). */
  public Instant recordSignal(Instant at) {
    Instant previous = lastSignalAt;
    lastSignalAt = at;
    status = CameraStatus.ONLINE;
    updatedAt = at;
    return previous;
  }

  /** Marca la cámara sin conexión cuando deja de enviar señal de vida (RF-6.3). */
  public void markOffline(Instant at) {
    status = CameraStatus.OFFLINE;
    updatedAt = at;
  }

  /** Asigna el hash del token del dispositivo (leído de EDGE_DEVICE_TOKENS). */
  public void assignDeviceTokenHash(String tokenHash, Instant at) {
    deviceTokenHash = tokenHash;
    updatedAt = at;
  }

  /** Incrementa la versión de configuración tras un cambio de zonas (ETag del agente). */
  public void bumpConfigVersion(Instant at) {
    configVersion++;
    updatedAt = at;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getAddress() {
    return address;
  }

  public Point getLocation() {
    return location;
  }

  public Polygon getCoverageArea() {
    return coverageArea;
  }

  public CameraStatus getStatus() {
    return status;
  }

  public String getDeviceTokenHash() {
    return deviceTokenHash;
  }

  public Instant getLastSignalAt() {
    return lastSignalAt;
  }

  public int getFrameWidth() {
    return frameWidth;
  }

  public int getFrameHeight() {
    return frameHeight;
  }

  public long getConfigVersion() {
    return configVersion;
  }
}

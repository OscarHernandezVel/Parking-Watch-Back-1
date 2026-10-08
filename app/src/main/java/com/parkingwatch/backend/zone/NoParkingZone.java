package com.parkingwatch.backend.zone;

import com.parkingwatch.common.domain.ZoneType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Zona donde no se permite estacionar, dibujada sobre la imagen de la cámara (PB-04). */
@Entity
@Table(name = "no_parking_zones")
public class NoParkingZone {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "camera_id", nullable = false, length = 20)
  private String cameraId;

  @Column(nullable = false, length = 50)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "zone_type", nullable = false, length = 20)
  private ZoneType zoneType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "image_polygon", nullable = false, columnDefinition = "jsonb")
  private List<ImagePoint> polygon;

  @Column(name = "tolerance_seconds", nullable = false)
  private int toleranceSeconds;

  @Column(nullable = false)
  private boolean signaled;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected NoParkingZone() {}

  /** Crea una zona activa para la cámara. */
  public NoParkingZone(String cameraId, ZoneDraft draft, Instant now) {
    this.cameraId = cameraId;
    this.createdAt = now;
    apply(draft, now);
  }

  /** Aplica los datos del administrador y reactiva la zona. */
  public final void apply(ZoneDraft draft, Instant now) {
    this.name = draft.name().trim();
    this.zoneType = draft.zoneType();
    this.polygon = List.copyOf(draft.polygon());
    this.toleranceSeconds = draft.toleranceSeconds();
    this.signaled = draft.signaled();
    this.active = true;
    this.updatedAt = now;
  }

  /** Desactiva la zona sin borrarla: los reportes históricos la referencian. */
  public void deactivate(Instant now) {
    this.active = false;
    this.updatedAt = now;
  }

  public Long getId() {
    return id;
  }

  public String getCameraId() {
    return cameraId;
  }

  public String getName() {
    return name;
  }

  public ZoneType getZoneType() {
    return zoneType;
  }

  public List<ImagePoint> getPolygon() {
    return List.copyOf(polygon);
  }

  public int getToleranceSeconds() {
    return toleranceSeconds;
  }

  public boolean isSignaled() {
    return signaled;
  }

  public boolean isActive() {
    return active;
  }
}

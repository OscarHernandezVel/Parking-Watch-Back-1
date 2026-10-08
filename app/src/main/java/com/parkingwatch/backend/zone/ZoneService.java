package com.parkingwatch.backend.zone;

import com.parkingwatch.backend.camera.Camera;
import com.parkingwatch.backend.camera.CameraService;
import com.parkingwatch.backend.shared.EdgeConfigurationChanged;
import com.parkingwatch.backend.shared.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Configuración de zonas no autorizadas por cámara (PB-04, RF-6.1, RF-6.2). */
@Service
public class ZoneService {

  private static final Logger LOG = LoggerFactory.getLogger(ZoneService.class);

  private final CameraService cameras;
  private final ZoneRepository zones;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  public ZoneService(
      CameraService cameras, ZoneRepository zones, Clock clock, ApplicationEventPublisher events) {
    this.cameras = cameras;
    this.zones = zones;
    this.clock = clock;
    this.events = events;
  }

  /** Zonas activas de la cámara. */
  @Transactional(readOnly = true)
  public List<ZoneView> listActive(String cameraId) {
    cameras.require(cameraId);
    return zones.findByCameraIdAndActiveTrueOrderByIdAsc(cameraId).stream()
        .map(ZoneView::of)
        .toList();
  }

  /** Zona por identificador. */
  @Transactional(readOnly = true)
  public Optional<NoParkingZone> find(long zoneId) {
    return zones.findById(zoneId);
  }

  /**
   * Reemplaza las zonas activas: actualiza las que traen id, crea las nuevas (reutilizando el
   * nombre si existía inactiva) y desactiva las omitidas. Incrementa la versión de configuración y
   * envía el cambio al Edge por WebSocket para que lo aplique sin reiniciarse (RF-6.2).
   */
  @Transactional
  public List<ZoneView> replace(String cameraId, List<ZoneDraft> drafts) {
    Camera camera = cameras.require(cameraId);
    validate(drafts, camera);
    Instant now = clock.instant();
    Map<Long, NoParkingZone> existing =
        zones.findByCameraIdOrderByIdAsc(cameraId).stream()
            .collect(Collectors.toMap(NoParkingZone::getId, Function.identity()));
    Set<Long> kept = new HashSet<>();
    for (ZoneDraft draft : drafts) {
      kept.add(upsert(cameraId, draft, existing, now).getId());
    }
    existing.values().stream()
        .filter(zone -> !kept.contains(zone.getId()))
        .forEach(zone -> zone.deactivate(now));
    camera.bumpConfigVersion(now);
    zones.flush();
    events.publishEvent(new EdgeConfigurationChanged(cameraId));
    LOG.info("Zonas de {} actualizadas: {} activas", cameraId, kept.size());
    return listActive(cameraId);
  }

  private NoParkingZone upsert(
      String cameraId, ZoneDraft draft, Map<Long, NoParkingZone> existing, Instant now) {
    NoParkingZone target =
        draft.id() != null ? existing.get(draft.id()) : findByName(existing, draft.name());
    if (draft.id() != null && target == null) {
      throw new ValidationException(
          "La zona " + draft.id() + " no pertenece a la cámara " + cameraId);
    }
    if (target == null) {
      return zones.save(new NoParkingZone(cameraId, draft, now));
    }
    target.apply(draft, now);
    return target;
  }

  private static NoParkingZone findByName(Map<Long, NoParkingZone> existing, String name) {
    return existing.values().stream()
        .filter(zone -> zone.getName().equalsIgnoreCase(name.trim()))
        .findFirst()
        .orElse(null);
  }

  private static void validate(List<ZoneDraft> drafts, Camera camera) {
    Set<String> names = new HashSet<>();
    for (ZoneDraft draft : drafts) {
      if (!names.add(draft.name().trim().toLowerCase(Locale.ROOT))) {
        throw new ValidationException(
            "El nombre de zona '" + draft.name().trim() + "' está repetido");
      }
      ImagePolygon.of(draft.polygon(), camera.getFrameWidth(), camera.getFrameHeight());
    }
  }
}

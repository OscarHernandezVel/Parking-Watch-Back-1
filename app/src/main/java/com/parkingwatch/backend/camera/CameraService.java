package com.parkingwatch.backend.camera;

import com.parkingwatch.backend.config.ParkingProperties;
import com.parkingwatch.backend.report.ReportRepository;
import com.parkingwatch.backend.shared.NotFoundException;
import com.parkingwatch.backend.shared.RealtimeEvent;
import com.parkingwatch.common.contract.HeartbeatMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestión de cámaras y de su estado de conexión (PB-01, PB-06). */
@Service
public class CameraService {

  private static final Logger LOG = LoggerFactory.getLogger(CameraService.class);

  private final CameraRepository cameras;
  private final ReportRepository reports;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final Duration heartbeatTimeout;

  public CameraService(
      CameraRepository cameras,
      ReportRepository reports,
      ApplicationEventPublisher events,
      Clock clock,
      ParkingProperties properties) {
    this.cameras = cameras;
    this.reports = reports;
    this.events = events;
    this.clock = clock;
    this.heartbeatTimeout = properties.rules().heartbeatTimeout();
  }

  @Transactional(readOnly = true)
  public List<CameraView> list() {
    Set<String> withOpenReports = reports.findCameraIdsWithOpenReports();
    Instant now = clock.instant();
    return cameras.findAllByOrderByIdAsc().stream()
        .map(camera -> view(camera, now, withOpenReports))
        .toList();
  }

  @Transactional(readOnly = true)
  public CameraView get(String cameraId) {
    return view(require(cameraId), clock.instant(), reports.findCameraIdsWithOpenReports());
  }

  /** Cámara existente o error 404. */
  @Transactional(readOnly = true)
  public Camera require(String cameraId) {
    return cameras.findById(cameraId).orElseThrow(() -> new NotFoundException("Cámara", cameraId));
  }

  /** Indica si la cámara envió señal de vida dentro del tiempo de espera. */
  public boolean isConnected(Camera camera) {
    return CameraDisplayStatus.isConnected(
        camera.getLastSignalAt(), clock.instant(), heartbeatTimeout);
  }

  /** Registra la señal de vida; si la cámara vuelve a estar en línea lo notifica (RF-6.3). */
  @Transactional
  public void recordHeartbeat(String cameraId, HeartbeatMessage message) {
    Camera camera = require(cameraId);
    Instant now = clock.instant();
    Instant previous = camera.recordSignal(now);
    if (!CameraDisplayStatus.isConnected(previous, now, heartbeatTimeout)) {
      LOG.info("Cámara {} en línea con el modelo {}", cameraId, message.modelVersion());
      events.publishEvent(
          new RealtimeEvent(
              RealtimeEvent.CAMERAS,
              "camera.status-changed",
              Map.of("cameraId", cameraId, "status", CameraStatus.ONLINE, "at", now)));
    }
  }

  /**
   * Marca "Sin conexión" las cámaras cuyo Edge no envió señal de vida en 30 s (RF-6.3) y avisa al
   * mapa por WebSocket. Retorna la cantidad de cámaras marcadas.
   */
  @Transactional
  public int markDisconnected() {
    Instant now = clock.instant();
    int marked = 0;
    for (Camera camera : cameras.findByStatus(CameraStatus.ONLINE)) {
      if (!CameraDisplayStatus.isConnected(camera.getLastSignalAt(), now, heartbeatTimeout)) {
        camera.markOffline(now);
        LOG.warn("Cámara {} sin conexión desde {}", camera.getId(), camera.getLastSignalAt());
        events.publishEvent(
            new RealtimeEvent(
                RealtimeEvent.CAMERAS,
                "camera.status-changed",
                Map.of("cameraId", camera.getId(), "status", CameraStatus.OFFLINE, "at", now)));
        marked++;
      }
    }
    return marked;
  }

  private CameraView view(Camera camera, Instant now, Set<String> withOpenReports) {
    CameraDisplayStatus status =
        CameraDisplayStatus.resolve(
            camera.getLastSignalAt(),
            now,
            withOpenReports.contains(camera.getId()),
            heartbeatTimeout);
    return CameraView.of(camera, status);
  }
}

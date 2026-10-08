package com.parkingwatch.backend.live;

import com.parkingwatch.backend.camera.Camera;
import com.parkingwatch.backend.camera.CameraService;
import com.parkingwatch.backend.realtime.BrowserTopics;
import com.parkingwatch.backend.realtime.RealtimeBroadcaster;
import com.parkingwatch.common.contract.AnalyticsSnapshot;
import com.parkingwatch.common.contract.DetectionSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Detecciones (RF-2.2) e indicadores en tiempo real (RF-2.3, PB-14). Cada muestra del Edge se
 * retransmite al instante a las páginas suscritas y se guarda la última en memoria para quien abre
 * la vista después; el histórico por minuto se guarda aparte (RF-9.2).
 */
@Service
public class LiveStateService {

  private final CameraService cameras;
  private final RealtimeBroadcaster broadcaster;
  private final Clock clock;
  private final Map<String, LiveSnapshot> states = new ConcurrentHashMap<>();

  public LiveStateService(CameraService cameras, RealtimeBroadcaster broadcaster, Clock clock) {
    this.cameras = cameras;
    this.broadcaster = broadcaster;
    this.clock = clock;
  }

  private record LiveSnapshot(
      DetectionSnapshot detections,
      Instant detectionsAt,
      AnalyticsSnapshot analytics,
      Instant analyticsAt) {

    static final LiveSnapshot EMPTY = new LiveSnapshot(null, null, null, null);
  }

  /** Guarda y retransmite las detecciones (cada 200 ms). */
  public void saveDetections(String cameraId, DetectionSnapshot snapshot) {
    Instant now = clock.instant();
    states.merge(
        cameraId,
        new LiveSnapshot(snapshot, now, null, null),
        (old, fresh) -> new LiveSnapshot(snapshot, now, old.analytics(), old.analyticsAt()));
    broadcaster.send(BrowserTopics.detections(cameraId), snapshot);
  }

  /** Guarda y retransmite los indicadores (cada 5 s). */
  public void saveAnalytics(String cameraId, AnalyticsSnapshot snapshot) {
    Instant now = clock.instant();
    states.merge(
        cameraId,
        new LiveSnapshot(null, null, snapshot, now),
        (old, fresh) -> new LiveSnapshot(old.detections(), old.detectionsAt(), snapshot, now));
    broadcaster.send(BrowserTopics.analytics(cameraId), snapshot);
  }

  /** Último estado en vivo, con "sin conexión" y la hora de la última señal (HU-2). */
  public LiveView get(String cameraId) {
    Camera camera = cameras.require(cameraId);
    LiveSnapshot state = states.getOrDefault(cameraId, LiveSnapshot.EMPTY);
    return new LiveView(
        cameraId,
        cameras.isConnected(camera),
        camera.getLastSignalAt(),
        state.detections(),
        state.detectionsAt(),
        state.analytics(),
        state.analyticsAt());
  }
}

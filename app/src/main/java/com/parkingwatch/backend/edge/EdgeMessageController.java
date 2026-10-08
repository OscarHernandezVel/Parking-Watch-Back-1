package com.parkingwatch.backend.edge;

import com.parkingwatch.backend.camera.CameraService;
import com.parkingwatch.backend.ingestion.EventIngestionService;
import com.parkingwatch.backend.learning.DriftMonitoringService;
import com.parkingwatch.backend.live.LiveStateService;
import com.parkingwatch.backend.realtime.RealtimeBroadcaster;
import com.parkingwatch.backend.statistics.StatisticsService;
import com.parkingwatch.common.contract.AnalyticsSnapshot;
import com.parkingwatch.common.contract.DetectionSnapshot;
import com.parkingwatch.common.contract.DriftAlertMessage;
import com.parkingwatch.common.contract.EdgeStomp;
import com.parkingwatch.common.contract.EventBatch;
import com.parkingwatch.common.contract.EventBatchResult;
import com.parkingwatch.common.contract.HeartbeatMessage;
import com.parkingwatch.common.contract.MinuteStatisticsMessage;
import jakarta.validation.Valid;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;

/**
 * Mensajes que el Edge envía por WebSocket STOMP (RF-8.1, RF-11.4): eventos de cambio, detecciones,
 * video, indicadores, señal de vida, resúmenes por minuto y alertas de deriva. La cámara se toma de
 * la identidad autenticada en el CONNECT, nunca del mensaje.
 */
@Controller
@MessageMapping("/edge")
public class EdgeMessageController {

  private static final Logger LOG = LoggerFactory.getLogger(EdgeMessageController.class);
  private static final int JPEG_MARKER = 0xFF;
  private static final int JPEG_START = 0xD8;

  private final EventIngestionService ingestion;
  private final EdgeServices services;
  private final RealtimeBroadcaster broadcaster;

  public EdgeMessageController(
      EventIngestionService ingestion, EdgeServices services, RealtimeBroadcaster broadcaster) {
    this.ingestion = ingestion;
    this.services = services;
    this.broadcaster = broadcaster;
  }

  /** Servicios que reciben la telemetría del Edge. */
  @Component
  public record EdgeServices(
      CameraService cameras,
      StatisticsService statistics,
      LiveStateService live,
      DriftMonitoringService drift) {}

  /** Eventos de cambio; el resultado vuelve a la cola del Edge para que limpie su búfer. */
  @MessageMapping("/events")
  public void events(@Valid @Payload EventBatch batch, Principal device) {
    String cameraId = device.getName();
    EventBatchResult result = new EventBatchResult(ingestion.ingest(cameraId, batch.events()));
    broadcaster.sendToUser(cameraId, EdgeStomp.EVENT_RESULTS_QUEUE, result);
  }

  /** Detecciones cada 200 ms, retransmitidas a la vista en vivo (RF-2.2). */
  @MessageMapping("/detections")
  public void detections(@Valid @Payload DetectionSnapshot snapshot, Principal device) {
    services.live().saveDetections(device.getName(), snapshot);
  }

  /** Fotograma JPEG reducido, retransmitido a los navegadores suscritos (RF-2.1). */
  @MessageMapping("/video")
  public void video(@Payload byte[] jpeg, Principal device) {
    if (jpeg.length < 2 || (jpeg[0] & 0xFF) != JPEG_MARKER || (jpeg[1] & 0xFF) != JPEG_START) {
      LOG.debug("Fotograma descartado de {}: no es JPEG", device.getName());
      return;
    }
    broadcaster.sendVideoFrame(device.getName(), jpeg);
  }

  /** Indicadores en tiempo real cada 5 s (PB-14). */
  @MessageMapping("/analytics")
  public void analytics(@Valid @Payload AnalyticsSnapshot snapshot, Principal device) {
    services.live().saveAnalytics(device.getName(), snapshot);
  }

  /** Señal de vida; sin ella durante 30 s la cámara queda "Sin conexión" (RF-6.3). */
  @MessageMapping("/heartbeat")
  public void heartbeat(@Valid @Payload HeartbeatMessage message, Principal device) {
    services.cameras().recordHeartbeat(device.getName(), message);
  }

  /** Resumen por minuto para el histórico (RF-9.2). */
  @MessageMapping("/statistics")
  public void statistics(@Valid @Payload MinuteStatisticsMessage message, Principal device) {
    services.statistics().ingestMinute(device.getName(), message);
  }

  /** Alerta de deriva calculada en el borde (RF-16.4). */
  @MessageMapping("/alerts")
  public void alerts(@Valid @Payload DriftAlertMessage message, Principal device) {
    services.drift().recordEdgeAlert(device.getName(), message);
  }

  /** Un mensaje inválido se descarta sin cerrar la sesión del Edge. */
  @MessageExceptionHandler
  public void handleError(Exception error, Principal device) {
    String cameraId = device == null ? "desconocida" : device.getName();
    LOG.warn("Mensaje del Edge {} descartado: {}", cameraId, error.getMessage());
  }
}

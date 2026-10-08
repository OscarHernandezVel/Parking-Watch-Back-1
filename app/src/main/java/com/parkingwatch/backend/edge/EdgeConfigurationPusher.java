package com.parkingwatch.backend.edge;

import com.parkingwatch.backend.camera.CameraService;
import com.parkingwatch.backend.camera.CameraView;
import com.parkingwatch.backend.realtime.RealtimeBroadcaster;
import com.parkingwatch.backend.shared.EdgeConfigurationChanged;
import com.parkingwatch.common.contract.EdgeStomp;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía al instante al Edge, por WebSocket, los cambios de zonas y de modelo activo (RF-6.2, HU-4,
 * HU-5): el Edge los aplica sin reiniciarse. Al conectarse, el Edge descarga la configuración
 * vigente por HTTPS, así que un cambio hecho mientras estaba desconectado no se pierde.
 */
@Component
public class EdgeConfigurationPusher {

  private static final Logger LOG = LoggerFactory.getLogger(EdgeConfigurationPusher.class);

  private final EdgeConfigurationService configurations;
  private final CameraService cameras;
  private final RealtimeBroadcaster broadcaster;

  public EdgeConfigurationPusher(
      EdgeConfigurationService configurations,
      CameraService cameras,
      RealtimeBroadcaster broadcaster) {
    this.configurations = configurations;
    this.cameras = cameras;
    this.broadcaster = broadcaster;
  }

  /** Tras confirmarse el cambio, envía la configuración a la cola de cada Edge afectado. */
  @TransactionalEventListener(fallbackExecution = true)
  public void onChange(EdgeConfigurationChanged change) {
    List<String> targets =
        change.cameraId() == null
            ? cameras.list().stream().map(CameraView::id).toList()
            : List.of(change.cameraId());
    for (String cameraId : targets) {
      broadcaster.sendToUser(
          cameraId,
          EdgeStomp.CONFIG_QUEUE,
          configurations.configurationFor(cameraId).configuration());
      LOG.info("Configuración enviada al Edge de {}", cameraId);
    }
  }
}

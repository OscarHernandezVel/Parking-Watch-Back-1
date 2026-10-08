package com.parkingwatch.backend.camera;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Revisa cada 5 s qué cámaras dejaron de enviar señal de vida (RF-6.3). */
@Component
public class CameraConnectionMonitor {

  private final CameraService cameras;

  public CameraConnectionMonitor(CameraService cameras) {
    this.cameras = cameras;
  }

  @Scheduled(fixedDelayString = "${parking.rules.connection-check-ms:5000}")
  public void check() {
    cameras.markDisconnected();
  }
}

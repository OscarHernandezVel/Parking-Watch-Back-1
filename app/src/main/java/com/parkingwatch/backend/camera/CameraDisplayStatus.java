package com.parkingwatch.backend.camera;

import java.time.Duration;
import java.time.Instant;

/** Estado que se muestra en el mapa con color, texto e ícono (RF-1.2). */
public enum CameraDisplayStatus {
  ONLINE,
  OFFLINE,
  ACTIVE_REPORT;

  /**
   * Deriva el estado al momento de la consulta (RF-6.3): sin señal de vida durante el tiempo de
   * espera, la cámara está "sin conexión"; así no hace falta un proceso que la marque.
   */
  public static CameraDisplayStatus resolve(
      Instant lastSignalAt, Instant now, boolean hasOpenReport, Duration timeout) {
    if (!isConnected(lastSignalAt, now, timeout)) {
      return OFFLINE;
    }
    return hasOpenReport ? ACTIVE_REPORT : ONLINE;
  }

  /** Indica si la última señal está dentro del tiempo de espera. */
  public static boolean isConnected(Instant lastSignalAt, Instant now, Duration timeout) {
    return lastSignalAt != null && !lastSignalAt.plus(timeout).isBefore(now);
  }
}

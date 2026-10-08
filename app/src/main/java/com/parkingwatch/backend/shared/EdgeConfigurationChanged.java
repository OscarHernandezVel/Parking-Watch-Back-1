package com.parkingwatch.backend.shared;

/**
 * Cambió la configuración que usa el Edge: zonas de una cámara o modelo activo (RF-6.2). Tras
 * confirmarse la transacción, el backend envía la nueva configuración por WebSocket.
 *
 * @param cameraId cámara afectada, o null si el cambio aplica a todas (modelo activo)
 */
public record EdgeConfigurationChanged(String cameraId) {

  /** Cambio que afecta a todas las cámaras. */
  public static EdgeConfigurationChanged allCameras() {
    return new EdgeConfigurationChanged(null);
  }
}

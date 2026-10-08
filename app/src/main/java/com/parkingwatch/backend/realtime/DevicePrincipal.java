package com.parkingwatch.backend.realtime;

import java.security.Principal;

/**
 * Identidad de un Edge conectado por WebSocket: su nombre es el id de la cámara, de modo que el
 * backend le envía la configuración a la cola de usuario de esa cámara.
 *
 * @param cameraId cámara que atiende el dispositivo
 */
public record DevicePrincipal(String cameraId) implements Principal {

  @Override
  public String getName() {
    return cameraId;
  }
}

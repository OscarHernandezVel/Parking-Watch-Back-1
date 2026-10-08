package com.parkingwatch.backend.camera;

import com.parkingwatch.backend.security.DeviceRegistry;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Resuelve la cámara de un token de dispositivo para la autenticación del agente. */
@Component
public class CameraDeviceRegistry implements DeviceRegistry {

  private final CameraRepository cameras;

  public CameraDeviceRegistry(CameraRepository cameras) {
    this.cameras = cameras;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<String> cameraIdForTokenHash(String tokenHash) {
    return cameras.findByDeviceTokenHash(tokenHash).map(Camera::getId);
  }
}

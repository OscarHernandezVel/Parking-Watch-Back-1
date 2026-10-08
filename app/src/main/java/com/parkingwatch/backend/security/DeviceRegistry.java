package com.parkingwatch.backend.security;

import java.util.Optional;

/** Puerto para resolver la cámara asociada al hash de un token de dispositivo. */
public interface DeviceRegistry {

  Optional<String> cameraIdForTokenHash(String tokenHash);
}

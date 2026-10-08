package com.parkingwatch.backend.camera;

import com.parkingwatch.backend.config.ParkingProperties;
import com.parkingwatch.backend.security.DeviceTokens;
import com.parkingwatch.common.contract.EdgeApi;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los tokens de los dispositivos Edge se guardan solo como variable de entorno de Render ({@code
 * EDGE_DEVICE_TOKENS}, RNF-4.3). Al arrancar, se guarda en cada cámara el hash SHA-256 de su token;
 * el token en claro nunca llega a la base de datos. Rotar un token es cambiar la variable y
 * reiniciar el servicio.
 */
@Component
@Order(100)
public class DeviceTokenSynchronizer implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(DeviceTokenSynchronizer.class);

  private final CameraRepository cameras;
  private final Clock clock;
  private final String tokens;

  public DeviceTokenSynchronizer(
      CameraRepository cameras, Clock clock, ParkingProperties properties) {
    this.cameras = cameras;
    this.clock = clock;
    this.tokens = properties.devices().tokens();
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    parse(tokens)
        .forEach(
            (cameraId, token) ->
                cameras
                    .findById(cameraId)
                    .ifPresentOrElse(
                        camera -> assign(camera, DeviceTokens.hash(token)),
                        () ->
                            LOG.warn(
                                "EDGE_DEVICE_TOKENS menciona la cámara {} que no existe",
                                cameraId)));
  }

  /** Lee el formato {@code CAM-1=pwd_...,CAM-2=pwd_...}; rechaza tokens sin el prefijo pwd_. */
  public static Map<String, String> parse(String value) {
    Map<String, String> result = new LinkedHashMap<>();
    if (value == null || value.isBlank()) {
      return result;
    }
    for (String entry : value.split(",")) {
      String[] parts = entry.trim().split("=", 2);
      if (parts.length != 2
          || parts[0].isBlank()
          || !parts[1].trim().startsWith(EdgeApi.DEVICE_TOKEN_PREFIX)) {
        throw new IllegalArgumentException(
            "EDGE_DEVICE_TOKENS debe tener el formato CAMARA=pwd_token separado por comas");
      }
      result.put(parts[0].trim(), parts[1].trim());
    }
    return result;
  }

  private void assign(Camera camera, String hash) {
    if (!hash.equals(camera.getDeviceTokenHash())) {
      camera.assignDeviceTokenHash(hash, clock.instant());
      LOG.info("Token de dispositivo actualizado para la cámara {}", camera.getId());
    }
  }
}

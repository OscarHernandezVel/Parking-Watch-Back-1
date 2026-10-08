package com.parkingwatch.backend.bootstrap;

import com.parkingwatch.backend.camera.Camera;
import com.parkingwatch.backend.camera.CameraRepository;
import com.parkingwatch.backend.model.ModelMetrics;
import com.parkingwatch.backend.model.ModelVersion;
import com.parkingwatch.backend.model.ModelVersionService;
import com.parkingwatch.backend.security.AppUser;
import com.parkingwatch.backend.security.AppUserRepository;
import com.parkingwatch.backend.security.AuthService;
import com.parkingwatch.backend.security.Role;
import com.parkingwatch.backend.zone.ZoneService;
import java.time.Clock;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga idempotente de los datos de la demostración: cámara de la maqueta, zonas, modelo y
 * administrador. Se activa con {@code parking.bootstrap.enabled=true}; las credenciales llegan por
 * variables de entorno, nunca en el código. El token del Edge lo asigna después {@code
 * DeviceTokenSynchronizer} desde {@code EDGE_DEVICE_TOKENS}.
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "parking.bootstrap.enabled", havingValue = "true")
public class PrototypeDataInitializer implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(PrototypeDataInitializer.class);
  private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

  private final CameraRepository cameras;
  private final ZoneService zones;
  private final ModelVersionService models;
  private final AppUserRepository users;
  private final AuthService auth;
  private final Clock clock;
  private final BootstrapSecrets secrets;

  public PrototypeDataInitializer(
      CameraRepository cameras,
      ZoneService zones,
      ModelVersionService models,
      AppUserRepository users,
      AuthService auth,
      Clock clock,
      BootstrapSecrets secrets) {
    this.cameras = cameras;
    this.zones = zones;
    this.models = models;
    this.users = users;
    this.auth = auth;
    this.clock = clock;
    this.secrets = secrets;
  }

  /** Credenciales de arranque leídas de variables de entorno. */
  @Component
  @ConditionalOnProperty(name = "parking.bootstrap.enabled", havingValue = "true")
  public record BootstrapSecrets(
      @Value("${parking.bootstrap.admin-username}") String adminUsername,
      @Value("${parking.bootstrap.admin-password}") String adminPassword,
      @Value("${parking.bootstrap.admin-totp-secret:}") String adminTotpSecret) {}

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (cameras.findById(PrototypeData.CAMERA_ID).isEmpty()) {
      cameras.saveAndFlush(new Camera(registration(), clock.instant()));
      zones.replace(PrototypeData.CAMERA_ID, PrototypeData.zones());
      LOG.info("Cámara de la maqueta {} registrada", PrototypeData.CAMERA_ID);
    }
    if (models.list().stream().noneMatch(m -> m.version().equals(PrototypeData.MODEL_VERSION))) {
      models.register(
          new ModelVersion.ModelRegistration(
              PrototypeData.MODEL_VERSION,
              clock.instant(),
              new ModelMetrics(0.92, 0.96, 0.91, 0.90),
              null,
              null,
              null,
              null));
      models.activate(PrototypeData.MODEL_VERSION, null);
    }
    if (!users.existsByUsername(AppUser.normalize(secrets.adminUsername()))) {
      auth.createUser(
          secrets.adminUsername(),
          "Administrador de la maqueta",
          secrets.adminPassword(),
          Role.ADMINISTRATOR);
      if (!secrets.adminTotpSecret().isBlank()) {
        auth.enableMfaWithSecret(secrets.adminUsername(), secrets.adminTotpSecret());
      }
      LOG.info("Administrador inicial creado");
    }
  }

  private Camera.CameraRegistration registration() {
    var location = WGS84.createPoint(new Coordinate(-74.08175, 4.60971));
    var coverage =
        WGS84.createPolygon(
            new Coordinate[] {
              new Coordinate(-74.0819, 4.6098),
              new Coordinate(-74.0816, 4.6098),
              new Coordinate(-74.0816, 4.6096),
              new Coordinate(-74.0819, 4.6096),
              new Coordinate(-74.0819, 4.6098)
            });
    return new Camera.CameraRegistration(
        PrototypeData.CAMERA_ID,
        "Cámara de la maqueta",
        "Maqueta de vía pública - sala de demostración",
        location,
        coverage,
        1280,
        720,
        null);
  }
}

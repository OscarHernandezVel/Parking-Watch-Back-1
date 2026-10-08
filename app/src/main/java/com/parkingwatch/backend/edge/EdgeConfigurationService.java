package com.parkingwatch.backend.edge;

import com.parkingwatch.backend.camera.Camera;
import com.parkingwatch.backend.camera.CameraService;
import com.parkingwatch.backend.config.ParkingProperties;
import com.parkingwatch.backend.model.ModelArtifactKind;
import com.parkingwatch.backend.model.ModelVersion;
import com.parkingwatch.backend.model.ModelVersionService;
import com.parkingwatch.backend.zone.ZoneRepository;
import com.parkingwatch.common.contract.EdgeApi;
import com.parkingwatch.common.contract.EdgeConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuración que consume el Edge (RF-6.2): zonas activas, modelo vigente con la ruta y huella de
 * sus archivos ONNX y parámetros de comunicación, versionada con un ETag. Se entrega al conectarse
 * y con cada cambio por WebSocket, y por HTTPS como respaldo.
 */
@Service
public class EdgeConfigurationService {

  private final CameraService cameras;
  private final ZoneRepository zones;
  private final ModelVersionService models;
  private final EdgeConfiguration.Settings settings;

  public EdgeConfigurationService(
      CameraService cameras,
      ZoneRepository zones,
      ModelVersionService models,
      ParkingProperties properties) {
    this.cameras = cameras;
    this.zones = zones;
    this.models = models;
    this.settings = properties.edge();
  }

  /** Configuración con su ETag. */
  public record VersionedConfiguration(String etag, EdgeConfiguration configuration) {}

  @Transactional(readOnly = true)
  public VersionedConfiguration configurationFor(String cameraId) {
    Camera camera = cameras.require(cameraId);
    Optional<ModelVersion> model = models.active();
    EdgeConfiguration configuration =
        new EdgeConfiguration(
            cameraId,
            camera.getConfigVersion(),
            new EdgeConfiguration.FrameSize(camera.getFrameWidth(), camera.getFrameHeight()),
            zones.findByCameraIdAndActiveTrueOrderByIdAsc(cameraId).stream()
                .map(
                    zone ->
                        new EdgeConfiguration.ZoneDefinition(
                            zone.getId(),
                            zone.getName(),
                            zone.getZoneType(),
                            zone.getPolygon().stream()
                                .map(p -> new EdgeConfiguration.Point(p.x(), p.y()))
                                .toList(),
                            zone.getToleranceSeconds(),
                            zone.isSignaled()))
                .toList(),
            model.map(active -> describe(cameraId, active)).orElse(null),
            settings);
    String version =
        camera.getConfigVersion() + ":" + model.map(ModelVersion::getVersion).orElse("none");
    return new VersionedConfiguration(etag(version), configuration);
  }

  /** Modelo activo con la ruta de descarga de cada archivo para la cámara (RF-15.3). */
  @Transactional(readOnly = true)
  public Optional<EdgeConfiguration.ActiveModel> activeModelFor(String cameraId) {
    cameras.require(cameraId);
    return models.active().map(model -> describe(cameraId, model));
  }

  private static EdgeConfiguration.ActiveModel describe(String cameraId, ModelVersion model) {
    return new EdgeConfiguration.ActiveModel(
        model.getVersion(),
        artifact(cameraId, model, ModelArtifactKind.DETECTOR, model.getDetectorSha256()),
        artifact(cameraId, model, ModelArtifactKind.PLATE_READER, model.getPlateReaderSha256()));
  }

  private static EdgeConfiguration.ModelArtifact artifact(
      String cameraId, ModelVersion model, ModelArtifactKind kind, String sha256) {
    if (sha256 == null) {
      return null;
    }
    String path = EdgeApi.modelArtifactPath(cameraId, model.getVersion(), kind.pathName());
    return new EdgeConfiguration.ModelArtifact(path, sha256);
  }

  /** ETag débil: cambia cuando cambian las zonas o el modelo activo. */
  private static String etag(String version) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(version.getBytes(StandardCharsets.UTF_8));
      return "W/\"" + HexFormat.of().formatHex(digest).substring(0, 16) + "\"";
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no disponible", e);
    }
  }
}

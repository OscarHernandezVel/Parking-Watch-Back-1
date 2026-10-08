package com.parkingwatch.backend.model;

import com.parkingwatch.backend.learning.Alert;
import com.parkingwatch.backend.learning.AlertService;
import com.parkingwatch.backend.shared.ConflictException;
import com.parkingwatch.backend.shared.EdgeConfigurationChanged;
import com.parkingwatch.backend.shared.NotFoundException;
import com.parkingwatch.backend.shared.QualityGateException;
import com.parkingwatch.backend.shared.ValidationException;
import com.parkingwatch.backend.storage.ObjectStorage;
import com.parkingwatch.common.domain.AlertType;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro, archivos, activación y reversión de versiones del modelo (PB-09, RF-9.1, HU-5). Cada
 * activación se envía al Edge por WebSocket, que descarga el modelo y lo carga sin detener el
 * video.
 */
@Service
public class ModelVersionService {

  private static final Logger LOG = LoggerFactory.getLogger(ModelVersionService.class);

  private final ModelVersionRepository models;
  private final ModelActivationRepository activations;
  private final ModelQualityGate gate;
  private final AlertService alerts;
  private final Clock clock;
  private final ObjectStorage storage;
  private final ApplicationEventPublisher events;

  public ModelVersionService(
      ModelVersionRepository models,
      ModelActivationRepository activations,
      ModelQualityGate gate,
      AlertService alerts,
      Clock clock,
      ObjectStorage storage,
      ApplicationEventPublisher events) {
    this.models = models;
    this.activations = activations;
    this.gate = gate;
    this.alerts = alerts;
    this.clock = clock;
    this.storage = storage;
    this.events = events;
  }

  @Transactional(readOnly = true)
  public List<ModelVersionView> list() {
    return models.findAllByOrderByTrainedAtDesc().stream()
        .map(model -> ModelVersionView.of(model, gate))
        .toList();
  }

  /** Versión activa, si existe. */
  @Transactional(readOnly = true)
  public Optional<ModelVersion> active() {
    return models.findByActiveTrue();
  }

  /** Registra una versión entrenada (desde el pipeline de entrenamiento o MLflow). */
  @Transactional
  public ModelVersionView register(ModelVersion.ModelRegistration registration) {
    if (models.existsById(registration.version())) {
      throw new ConflictException("La versión " + registration.version() + " ya está registrada");
    }
    ModelVersion model = models.save(new ModelVersion(registration, true, clock.instant()));
    LOG.info("Versión de modelo {} registrada", model.getVersion());
    return ModelVersionView.of(model, gate);
  }

  /** Activa una versión solo si está verificada y cumple las métricas mínimas. */
  @Transactional
  public ModelVersionView activate(String version, UUID actorId) {
    ModelVersion model = require(version);
    if (!model.isVerified()) {
      throw new ValidationException("La versión " + version + " no está verificada");
    }
    ModelQualityGate.Result result = gate.evaluate(model.metrics());
    if (!result.passed()) {
      throw new QualityGateException(version, result.failures());
    }
    models
        .findByActiveTrue()
        .filter(current -> !current.getVersion().equals(model.getVersion()))
        .ifPresent(current -> current.setActive(false));
    models.flush();
    model.setActive(true);
    activations.save(new ModelActivation(version, actorId, clock.instant()));
    events.publishEvent(EdgeConfigurationChanged.allCameras());
    LOG.info("Versión de modelo {} activada", version);
    return ModelVersionView.of(model, gate);
  }

  /**
   * Guarda un archivo ONNX de la versión en el disco persistente y calcula su SHA-256, con el que
   * el Edge verifica la descarga (RF-15.3). Si la versión ya está activa, se avisa al Edge.
   */
  @Transactional
  public ModelVersionView uploadArtifact(
      String version, ModelArtifactKind kind, InputStream content) {
    ModelVersion model = require(version);
    String key = kind.storageKey(version);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (DigestInputStream input = new DigestInputStream(content, digest)) {
        storage.put(key, input);
      }
      model.attachArtifact(kind, key, HexFormat.of().formatHex(digest.digest()));
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo guardar el modelo " + key, e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no disponible", e);
    }
    if (model.isActive()) {
      events.publishEvent(EdgeConfigurationChanged.allCameras());
    }
    LOG.info("Archivo {} de la versión {} guardado", kind.pathName(), version);
    return ModelVersionView.of(model, gate);
  }

  /** Abre un archivo ONNX de una versión para que el Edge lo descargue. */
  @Transactional(readOnly = true)
  public ModelArtifactFile openArtifact(String version, ModelArtifactKind kind) {
    ModelVersion model = require(version);
    String key =
        kind == ModelArtifactKind.DETECTOR
            ? model.getDetectorObjectKey()
            : model.getPlateReaderObjectKey();
    if (key == null || !storage.exists(key)) {
      throw new NotFoundException("Archivo del modelo", version + "/" + kind.pathName());
    }
    try {
      return new ModelArtifactFile(storage.size(key), storage.open(key));
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo leer el modelo " + key, e);
    }
  }

  /** Archivo ONNX abierto con su tamaño; el llamador debe cerrar el flujo. */
  public record ModelArtifactFile(long size, InputStream content) {}

  /** Vuelve a la versión activada antes de la vigente ("volver atrás con un comando"). */
  @Transactional
  public ModelVersionView rollback(UUID actorId) {
    String current = models.findByActiveTrue().map(ModelVersion::getVersion).orElse(null);
    String previous =
        activations.findLatest(PageRequest.of(0, 50)).stream()
            .map(ModelActivation::getVersion)
            .filter(version -> !version.equals(current))
            .findFirst()
            .orElseThrow(
                () -> new ConflictException("No hay una versión anterior a la cual volver"));
    return activate(previous, actorId);
  }

  /**
   * Resuelve la versión que generó un reporte. Una versión desconocida se registra como no
   * verificada y se alerta al administrador, sin perder el reporte.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public String resolveForReport(String version, String cameraId) {
    if (models.existsById(version)) {
      return version;
    }
    ModelVersion.ModelRegistration unknown =
        new ModelVersion.ModelRegistration(
            version, clock.instant(), new ModelMetrics(0, 0, 0, 0), null, null, null, null);
    models.saveAndFlush(new ModelVersion(unknown, false, clock.instant()));
    alerts.raiseOnce(
        new Alert.AlertDraft(
            AlertType.UNVERIFIED_MODEL,
            cameraId,
            "La cámara " + cameraId + " usa la versión no registrada " + version,
            null,
            null));
    return version;
  }

  private ModelVersion require(String version) {
    return models
        .findById(version)
        .orElseThrow(() -> new NotFoundException("Versión de modelo", version));
  }
}

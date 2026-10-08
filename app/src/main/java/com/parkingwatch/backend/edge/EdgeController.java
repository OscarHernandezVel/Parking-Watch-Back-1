package com.parkingwatch.backend.edge;

import com.parkingwatch.backend.ingestion.EventIngestionService;
import com.parkingwatch.backend.model.ModelArtifactKind;
import com.parkingwatch.backend.model.ModelVersionService;
import com.parkingwatch.backend.shared.NotFoundException;
import com.parkingwatch.common.contract.EdgeApi;
import com.parkingwatch.common.contract.EdgeConfiguration;
import com.parkingwatch.common.contract.EventBatch;
import com.parkingwatch.common.contract.EventBatchResult;
import com.parkingwatch.common.contract.EvidenceKeys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * API HTTPS del Edge (RF-8.3, RF-15.3): configuración inicial, fotos de evidencia, eventos
 * guardados sin conexión y descarga del modelo activo. El tráfico en tiempo real viaja por el
 * WebSocket {@code /ws/edge}. La autenticación por token de dispositivo y la verificación de que la
 * cámara de la ruta es la del token las hace DeviceAuthenticationFilter.
 */
@RestController
@RequestMapping(EdgeApi.BASE)
@Tag(name = "Edge")
public class EdgeController {

  private final EdgeConfigurationService configurations;
  private final EvidenceService evidence;
  private final EventIngestionService ingestion;
  private final ModelVersionService models;

  public EdgeController(
      EdgeConfigurationService configurations,
      EvidenceService evidence,
      EventIngestionService ingestion,
      ModelVersionService models) {
    this.configurations = configurations;
    this.evidence = evidence;
    this.ingestion = ingestion;
    this.models = models;
  }

  @Operation(summary = "Configuración de zonas y modelo vigente, con ETag (RF-6.2)")
  @GetMapping(EdgeApi.CONFIGURATION)
  public ResponseEntity<EdgeConfiguration> configuration(
      @PathVariable String cameraId,
      @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
    EdgeConfigurationService.VersionedConfiguration versioned =
        configurations.configurationFor(cameraId);
    if (versioned.etag().equals(ifNoneMatch)) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(versioned.etag()).build();
    }
    return ResponseEntity.ok()
        .eTag(versioned.etag())
        .cacheControl(CacheControl.noCache())
        .body(versioned.configuration());
  }

  @Operation(summary = "Eventos guardados sin conexión, en orden e idempotentes (RF-8.3)")
  @PostMapping(EdgeApi.EVENTS_BATCH)
  public EventBatchResult events(
      @PathVariable String cameraId, @Valid @RequestBody EventBatch batch) {
    return new EventBatchResult(ingestion.ingest(cameraId, batch.events()));
  }

  @Operation(summary = "Sube las tres fotos de evidencia de un reporte (multipart, RF-7.3)")
  @PostMapping(value = EdgeApi.EVIDENCE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public EvidenceKeys evidence(
      @PathVariable String cameraId,
      @RequestPart("entry") MultipartFile entry,
      @RequestPart("report") MultipartFile report,
      @RequestPart("plate") MultipartFile plate) {
    return evidence.store(cameraId, new EvidenceService.EvidencePhotos(entry, report, plate));
  }

  @Operation(summary = "Modelo activo con la huella SHA-256 de cada archivo (RF-9.1)")
  @GetMapping(EdgeApi.ACTIVE_MODEL)
  public EdgeConfiguration.ActiveModel activeModel(@PathVariable String cameraId) {
    return configurations
        .activeModelFor(cameraId)
        .orElseThrow(() -> new NotFoundException("Modelo activo", cameraId));
  }

  @Operation(summary = "Descarga un archivo ONNX del modelo (RF-15.3)")
  @GetMapping(EdgeApi.MODEL_ARTIFACT)
  public ResponseEntity<InputStreamResource> modelArtifact(
      @PathVariable String cameraId, @PathVariable String version, @PathVariable String artifact) {
    ModelArtifactKind kind =
        ModelArtifactKind.fromPath(artifact)
            .orElseThrow(() -> new NotFoundException("Archivo del modelo", artifact));
    ModelVersionService.ModelArtifactFile file = models.openArtifact(version, kind);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(file.size())
        .body(new InputStreamResource(file.content()));
  }
}

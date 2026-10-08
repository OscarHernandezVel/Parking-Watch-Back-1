package com.parkingwatch.backend.model;

import com.parkingwatch.backend.security.AuthenticatedUser;
import com.parkingwatch.backend.shared.ValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Versiones del modelo: registro, archivos ONNX, activación y reversión (administrador, PB-09). */
@RestController
@RequestMapping("/api/v1/models")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@Tag(name = "Modelos")
public class ModelController {

  private final ModelVersionService models;

  public ModelController(ModelVersionService models) {
    this.models = models;
  }

  /** Versión entrenada con sus métricas en el conjunto de prueba. */
  public record RegisterModelRequest(
      @NotNull @Pattern(regexp = "^[A-Za-z0-9._-]{1,20}$") String version,
      @NotNull Instant trainedAt,
      @DecimalMin("0.0") @DecimalMax("1.0") double map50,
      @DecimalMin("0.0") @DecimalMax("1.0") double precision,
      @DecimalMin("0.0") @DecimalMax("1.0") double recall,
      @DecimalMin("0.0") @DecimalMax("1.0") double plateAccuracy,
      @Pattern(regexp = "^models/.+") String detectorObjectKey,
      @Pattern(regexp = "^[a-f0-9]{64}$") String detectorSha256,
      @Pattern(regexp = "^models/.+") String plateReaderObjectKey,
      @Pattern(regexp = "^[a-f0-9]{64}$") String plateReaderSha256) {

    ModelVersion.ModelRegistration toRegistration() {
      return new ModelVersion.ModelRegistration(
          version,
          trainedAt,
          new ModelMetrics(map50, precision, recall, plateAccuracy),
          detectorObjectKey,
          detectorSha256,
          plateReaderObjectKey,
          plateReaderSha256);
    }
  }

  @Operation(summary = "Versiones del modelo con métricas y quality gate")
  @GetMapping
  public List<ModelVersionView> list() {
    return models.list();
  }

  @Operation(summary = "Registra una versión entrenada (RF-13.3)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ModelVersionView register(@Valid @RequestBody RegisterModelRequest request) {
    return models.register(request.toRegistration());
  }

  @Operation(summary = "Sube el archivo ONNX del detector o del lector de placas (RF-9.1)")
  @PostMapping(
      value = "/{version}/artifacts/{artifact}",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ModelVersionView uploadArtifact(
      @PathVariable String version,
      @PathVariable String artifact,
      @RequestPart("file") MultipartFile file)
      throws IOException {
    ModelArtifactKind kind =
        ModelArtifactKind.fromPath(artifact)
            .orElseThrow(
                () -> new ValidationException("Archivo de modelo desconocido: " + artifact));
    if (file.isEmpty()) {
      throw new ValidationException("El archivo del modelo está vacío");
    }
    try (InputStream content = file.getInputStream()) {
      return models.uploadArtifact(version, kind, content);
    }
  }

  @Operation(summary = "Activa una versión que cumple las métricas mínimas (HU-5)")
  @PostMapping("/{version}/activate")
  public ModelVersionView activate(@PathVariable String version, @AuthenticationPrincipal Jwt jwt) {
    return models.activate(version, AuthenticatedUser.from(jwt).id());
  }

  @Operation(summary = "Vuelve a la versión anterior")
  @PostMapping("/rollback")
  public ModelVersionView rollback(@AuthenticationPrincipal Jwt jwt) {
    return models.rollback(AuthenticatedUser.from(jwt).id());
  }
}

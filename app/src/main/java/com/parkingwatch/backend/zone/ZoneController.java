package com.parkingwatch.backend.zone;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Consulta y configuración de zonas no autorizadas (PB-04). */
@RestController
@Tag(name = "Zonas")
public class ZoneController {

  private final ZoneService zones;

  public ZoneController(ZoneService zones) {
    this.zones = zones;
  }

  /** Zonas a guardar para una cámara. */
  public record ReplaceZonesRequest(
      @NotNull @Size(max = 20) List<@Valid @NotNull ZoneDraft> zones) {}

  @Operation(summary = "Zonas activas de la cámara")
  @PreAuthorize("hasRole('OPERATOR')")
  @GetMapping("/api/v1/cameras/{cameraId}/zones")
  public List<ZoneView> list(@PathVariable String cameraId) {
    return zones.listActive(cameraId);
  }

  @Operation(summary = "Guarda las zonas de la cámara (administrador)")
  @PreAuthorize("hasRole('ADMINISTRATOR')")
  @PutMapping("/api/v1/cameras/{cameraId}/zones")
  public List<ZoneView> replace(
      @PathVariable String cameraId, @Valid @RequestBody ReplaceZonesRequest request) {
    return zones.replace(cameraId, request.zones());
  }
}

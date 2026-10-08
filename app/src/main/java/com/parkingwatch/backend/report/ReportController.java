package com.parkingwatch.backend.report;

import com.parkingwatch.backend.report.export.ExportFile;
import com.parkingwatch.backend.report.export.ExportFormat;
import com.parkingwatch.backend.security.AuthenticatedUser;
import com.parkingwatch.backend.shared.NotFoundException;
import com.parkingwatch.common.domain.EvidenceKind;
import com.parkingwatch.common.domain.ReportStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reportes generados por el reconocimiento: lista, detalle, revisión y exportación (PB-03). */
@RestController
@Validated
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasRole('OPERATOR')")
@Tag(name = "Reportes")
public class ReportController {

  private final ReportService reports;

  public ReportController(ReportService reports) {
    this.reports = reports;
  }

  @Operation(summary = "Reportes con filtros por fecha, cámara, zona y estado (RF-3.1)")
  @GetMapping
  public Page<ReportView> list(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) String cameraId,
      @RequestParam(required = false) Long zoneId,
      @RequestParam(required = false) ReportStatus status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
    return reports.search(
        new ReportFilter(from, to, cameraId, zoneId, status), PageRequest.of(page, size));
  }

  @Operation(summary = "Exporta el listado filtrado a PDF o Excel (RF-3.4)")
  @GetMapping("/export")
  public ResponseEntity<byte[]> export(
      @RequestParam ExportFormat format,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) String cameraId,
      @RequestParam(required = false) Long zoneId,
      @RequestParam(required = false) ReportStatus status) {
    ExportFile file = reports.export(new ReportFilter(from, to, cameraId, zoneId, status), format);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.filename()).build().toString())
        .body(file.body());
  }

  @Operation(summary = "Detalle con fotos y recorte de la placa (RF-3.2)")
  @GetMapping("/{id}")
  public ReportDetail detail(@PathVariable UUID id) {
    return reports.detail(id);
  }

  @Operation(summary = "Foto de evidencia: entry, report o plate (RF-3.2)")
  @GetMapping(value = "/{id}/evidence/{kind}", produces = MediaType.IMAGE_JPEG_VALUE)
  public ResponseEntity<InputStreamResource> evidence(
      @PathVariable UUID id, @PathVariable String kind) {
    EvidenceKind evidenceKind =
        Arrays.stream(EvidenceKind.values())
            .filter(value -> value.name().equalsIgnoreCase(kind))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Foto de evidencia", kind));
    ReportService.EvidencePhoto photo = reports.openEvidence(id, evidenceKind);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .contentLength(photo.size())
        .cacheControl(CacheControl.noStore())
        .body(new InputStreamResource(photo.content()));
  }

  @Operation(summary = "Confirma o descarta el reporte con motivo (RF-3.3)")
  @PatchMapping("/{id}")
  public ReportView review(
      @PathVariable UUID id,
      @Valid @RequestBody ReviewRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return reports.review(id, request, AuthenticatedUser.from(jwt));
  }
}

package com.parkingwatch.backend.report;

import com.parkingwatch.backend.learning.DriftMonitoringService;
import com.parkingwatch.backend.learning.RetrainingService;
import com.parkingwatch.backend.report.export.ExportFile;
import com.parkingwatch.backend.report.export.ExportFormat;
import com.parkingwatch.backend.report.export.ExportMetadata;
import com.parkingwatch.backend.report.export.ReportExporterFactory;
import com.parkingwatch.backend.report.export.ReportRow;
import com.parkingwatch.backend.security.AuthenticatedUser;
import com.parkingwatch.backend.shared.NotFoundException;
import com.parkingwatch.backend.shared.RealtimeEvent;
import com.parkingwatch.backend.storage.ObjectStorage;
import com.parkingwatch.backend.zone.NoParkingZone;
import com.parkingwatch.backend.zone.ZoneRepository;
import com.parkingwatch.common.domain.EvidenceKind;
import com.parkingwatch.common.domain.ReportStatus;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Consulta, revisión y exportación de reportes (PB-03). */
@Service
public class ReportService {

  private static final Logger LOG = LoggerFactory.getLogger(ReportService.class);
  private static final int MAX_EXPORT_ROWS = 5000;

  private final ReportRepository reports;
  private final ZoneRepository zones;
  private final ObjectStorage storage;
  private final ReportCollaborators collaborators;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public ReportService(
      ReportRepository reports,
      ZoneRepository zones,
      ObjectStorage storage,
      ReportCollaborators collaborators,
      ApplicationEventPublisher events,
      TransactionTemplate transactions,
      Clock clock) {
    this.reports = reports;
    this.zones = zones;
    this.storage = storage;
    this.collaborators = collaborators;
    this.events = events;
    this.transactions = transactions;
    this.clock = clock;
  }

  /** Servicios del aprendizaje continuo y de exportación que usa el flujo de reportes. */
  @Component
  public record ReportCollaborators(
      RetrainingService retraining,
      DriftMonitoringService drift,
      ReportExporterFactory exporters) {}

  @Transactional(readOnly = true)
  public Page<ReportView> search(ReportFilter filter, Pageable pageable) {
    Pageable sorted =
        PageRequest.of(
            pageable.getPageNumber(), pageable.getPageSize(), Sort.by("generatedAt").descending());
    return reports.findAll(filter.toSpecification(), sorted).map(ReportView::of);
  }

  /** Detalle con las rutas de las tres fotos, servidas por la API con la sesión (RF-3.2). */
  @Transactional(readOnly = true)
  public ReportDetail detail(UUID id) {
    Report report = require(id);
    ReportDetail.ZoneSummary zone =
        zones
            .findById(report.getZoneId())
            .map(z -> new ReportDetail.ZoneSummary(z.getId(), z.getName(), z.getZoneType()))
            .orElse(null);
    return new ReportDetail(ReportView.of(report), zone, ReportDetail.EvidenceUrls.forReport(id));
  }

  /** Abre una foto de evidencia del reporte desde el disco persistente. */
  @Transactional(readOnly = true)
  public EvidencePhoto openEvidence(UUID id, EvidenceKind kind) {
    Report report = require(id);
    String key =
        switch (kind) {
          case ENTRY -> report.getEntryPhotoKey();
          case REPORT -> report.getReportPhotoKey();
          case PLATE -> report.getPlatePhotoKey();
        };
    if (!storage.exists(key)) {
      throw new NotFoundException("Foto de evidencia", id + "/" + kind);
    }
    try {
      return new EvidencePhoto(storage.size(key), storage.open(key));
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo leer la evidencia " + key, e);
    }
  }

  /** Foto JPEG abierta con su tamaño; el llamador debe cerrar el flujo. */
  public record EvidencePhoto(long size, InputStream content) {}

  /**
   * Confirma o descarta (RF-3.3). La revisión se guarda en su propia transacción; luego, como mejor
   * esfuerzo, el descarte alimenta el dataset de reentrenamiento (RF-14.1).
   */
  public ReportView review(UUID id, ReviewRequest request, AuthenticatedUser reviewer) {
    Report report =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  Report target = require(id);
                  if (request.status() == ReportStatus.CONFIRMED) {
                    target.confirm(reviewer.id(), clock.instant());
                  } else {
                    target.dismiss(reviewer.id(), clock.instant(), request.dismissalReason());
                  }
                  return reports.save(target);
                }));
    LOG.info("Reporte {} revisado como {} por {}", id, report.getStatus(), reviewer.username());
    events.publishEvent(
        new RealtimeEvent(
            RealtimeEvent.REPORTS,
            "report.updated",
            Map.of("id", id, "status", report.getStatus())));
    if (report.getStatus() == ReportStatus.DISMISSED) {
      feedLearning(report, request.correctedPlate());
    }
    return ReportView.of(report);
  }

  /** Exporta el listado filtrado a PDF o Excel (RF-3.4). */
  @Transactional(readOnly = true)
  public ExportFile export(ReportFilter filter, ExportFormat format) {
    List<Report> found =
        reports
            .findAll(
                filter.toSpecification(),
                PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by("generatedAt").descending()))
            .getContent();
    Map<Long, String> zoneNames =
        zones.findAllById(found.stream().map(Report::getZoneId).distinct().toList()).stream()
            .collect(Collectors.toMap(NoParkingZone::getId, NoParkingZone::getName));
    List<ReportRow> rows = found.stream().map(rowMapper(zoneNames)).toList();
    ExportMetadata metadata =
        new ExportMetadata(
            "Reportes de parqueo en zonas no autorizadas", clock.instant(), filter.describe());
    return collaborators.exporters().create(format).export(rows, metadata);
  }

  private void feedLearning(Report report, String correctedPlate) {
    try {
      collaborators.retraining().collect(report, correctedPlate);
      collaborators.drift().checkDismissalRate();
    } catch (RuntimeException e) {
      LOG.error(
          "No se pudo agregar el reporte {} al dataset de reentrenamiento", report.getId(), e);
    }
  }

  private Report require(UUID id) {
    return reports.findById(id).orElseThrow(() -> new NotFoundException("Reporte", id));
  }

  private static Function<Report, ReportRow> rowMapper(Map<Long, String> zoneNames) {
    return report ->
        new ReportRow(
            report.getGeneratedAt(),
            report.getCameraId(),
            zoneNames.getOrDefault(report.getZoneId(), "Zona " + report.getZoneId()),
            report.getVehicleType(),
            report.getPlate(),
            report.dwellSeconds(),
            report.getStatus());
  }
}

package com.parkingwatch.backend.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkingwatch.backend.config.ParkingProperties;
import com.parkingwatch.backend.report.Report;
import com.parkingwatch.backend.storage.ObjectStorage;
import com.parkingwatch.common.domain.AlertType;
import com.parkingwatch.common.domain.DismissalReason;
import com.parkingwatch.common.domain.PlateNumber;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo humano-en-el-ciclo (RF-14.1, RF-14.3): los reportes descartados como falso positivo o placa
 * mal leída se copian al dataset con la etiqueta corregida. Al acumular el umbral de imágenes (200)
 * se alerta que hay que reentrenar.
 */
@Service
public class RetrainingService {

  private static final Logger LOG = LoggerFactory.getLogger(RetrainingService.class);

  private final RetrainingSampleRepository samples;
  private final ObjectStorage storage;
  private final AlertService alerts;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final int threshold;

  public RetrainingService(
      RetrainingSampleRepository samples,
      ObjectStorage storage,
      AlertService alerts,
      ObjectMapper mapper,
      Clock clock,
      ParkingProperties properties) {
    this.samples = samples;
    this.storage = storage;
    this.alerts = alerts;
    this.mapper = mapper;
    this.clock = clock;
    this.threshold = properties.learning().retrainingSampleThreshold();
  }

  /** Estado del reentrenamiento para el administrador. */
  public record RetrainingStatus(
      long pendingSamples, int threshold, long openDriftAlerts, boolean retrainingRequired) {}

  /** Agrega al dataset un reporte descartado; retorna false si el motivo no aplica. */
  @Transactional
  public boolean collect(Report report, String correctedPlate) {
    DismissalReason reason = report.getDismissalReason();
    if (reason == null || !reason.feedsRetraining() || samples.existsByReportId(report.getId())) {
      return false;
    }
    String base = "retraining/" + reason.name().toLowerCase(Locale.ROOT) + "/" + report.getId();
    String source =
        reason == DismissalReason.PLATE_MISREAD
            ? report.getPlatePhotoKey()
            : report.getReportPhotoKey();
    try {
      storage.copy(source, base + ".jpg");
      storage.putJson(base + ".json", label(report, reason, correctedPlate));
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo copiar la muestra " + base, e);
    }
    samples.save(
        new RetrainingSample(
            report.getId(), reason, base + ".jpg", base + ".json", clock.instant()));
    LOG.info("Reporte {} agregado al dataset de reentrenamiento ({})", report.getId(), reason);
    evaluateTrigger();
    return true;
  }

  @Transactional(readOnly = true)
  public RetrainingStatus status() {
    long pending = samples.countByConsumedAtIsNull();
    long drift = alerts.countOpenDriftAlerts();
    return new RetrainingStatus(pending, threshold, drift, pending >= threshold || drift > 0);
  }

  private void evaluateTrigger() {
    long pending = samples.countByConsumedAtIsNull();
    if (pending >= threshold) {
      alerts.raiseOnce(
          new Alert.AlertDraft(
              AlertType.RETRAINING_REQUIRED,
              null,
              "Se acumularon " + pending + " imágenes corregidas: se requiere reentrenar el modelo",
              (double) pending,
              (double) threshold));
    }
  }

  /**
   * Etiqueta corregida sin datos del funcionario ni de ubicación (minimización, Ley 1581 de 2012).
   */
  private String label(Report report, DismissalReason reason, String correctedPlate) {
    Map<String, Object> label = new LinkedHashMap<>();
    label.put("modelVersion", report.getModelVersion());
    if (reason == DismissalReason.PLATE_MISREAD) {
      label.put("task", "plate-ocr");
      label.put("predicted", report.getPlate());
      label.put("corrected", PlateNumber.normalize(correctedPlate).orElse(null));
    } else {
      label.put("task", "detection");
      label.put("vehicleType", report.getVehicleType());
      label.put("label", "NO_VIOLATION");
    }
    try {
      return mapper.writeValueAsString(label);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("No se pudo serializar la etiqueta", e);
    }
  }
}

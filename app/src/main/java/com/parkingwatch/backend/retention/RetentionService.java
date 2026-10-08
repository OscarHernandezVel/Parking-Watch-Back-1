package com.parkingwatch.backend.retention;

import com.parkingwatch.backend.config.ParkingProperties;
import com.parkingwatch.backend.report.Report;
import com.parkingwatch.backend.report.ReportRepository;
import com.parkingwatch.backend.storage.ObjectStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tratamiento de datos personales (RNF-2.2, Ley 1581 de 2012): los reportes descartados se borran a
 * los N días junto con sus fotos. Las copias anonimizadas del dataset no se ven afectadas.
 */
@Service
public class RetentionService {

  private static final Logger LOG = LoggerFactory.getLogger(RetentionService.class);
  private static final int BATCH_SIZE = 200;

  private final ReportRepository reports;
  private final ObjectStorage storage;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final Duration retention;

  public RetentionService(
      ReportRepository reports,
      ObjectStorage storage,
      TransactionTemplate transactions,
      Clock clock,
      ParkingProperties properties) {
    this.reports = reports;
    this.storage = storage;
    this.transactions = transactions;
    this.clock = clock;
    this.retention = Duration.ofDays(properties.retention().dismissedReportDays());
  }

  /** Purga diaria (02:00, hora de Colombia). Retorna la cantidad de reportes borrados. */
  @Scheduled(cron = "${parking.retention.cron:0 0 2 * * *}", zone = "America/Bogota")
  public int purgeDismissedReports() {
    Instant cutoff = clock.instant().minus(retention);
    int purged = 0;
    List<Report> batch;
    do {
      batch = reports.findDismissedBefore(cutoff, PageRequest.of(0, BATCH_SIZE));
      purged += purge(batch);
    } while (batch.size() == BATCH_SIZE);
    LOG.info("Retención ejecutada: {} reportes descartados borrados (corte {})", purged, cutoff);
    return purged;
  }

  private int purge(List<Report> batch) {
    if (batch.isEmpty()) {
      return 0;
    }
    try {
      storage.deleteAll(
          batch.stream()
              .flatMap(
                  r -> Stream.of(r.getEntryPhotoKey(), r.getReportPhotoKey(), r.getPlatePhotoKey()))
              .toList());
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudieron borrar las fotos de los reportes", e);
    }
    transactions.executeWithoutResult(status -> reports.deleteAllInBatch(batch));
    return batch.size();
  }
}

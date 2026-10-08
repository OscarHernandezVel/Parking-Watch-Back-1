package com.parkingwatch.backend.statistics;

import com.parkingwatch.backend.shared.ValidationException;
import com.parkingwatch.backend.zone.NoParkingZone;
import com.parkingwatch.backend.zone.ZoneRepository;
import com.parkingwatch.common.contract.MinuteStatisticsMessage;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Agregados por minuto, hora o día para consultar tendencias (PB-12, RF-12.2). */
@Service
public class StatisticsService {

  private static final Duration MAX_RANGE = Duration.ofDays(93);

  private final StatisticsRepository statistics;
  private final ZoneRepository zones;

  public StatisticsService(StatisticsRepository statistics, ZoneRepository zones) {
    this.statistics = statistics;
    this.zones = zones;
  }

  /** Guarda los agregados del minuto, ignorando zonas que no pertenecen a la cámara. */
  @Transactional
  public int ingestMinute(String cameraId, MinuteStatisticsMessage message) {
    Set<Long> ownZones =
        zones.findByCameraIdOrderByIdAsc(cameraId).stream()
            .map(NoParkingZone::getId)
            .collect(Collectors.toSet());
    List<MinuteStatisticsMessage.ZoneMinute> valid =
        message.zones().stream().filter(zone -> ownZones.contains(zone.zoneId())).toList();
    if (!valid.isEmpty()) {
      statistics.upsert(message.minute().truncatedTo(ChronoUnit.MINUTES), valid);
    }
    return valid.size();
  }

  /** Serie de tendencias en un rango de hasta 93 días. */
  @Transactional(readOnly = true)
  public List<StatisticsPoint> query(
      Instant from, Instant to, Long zoneId, Granularity granularity) {
    if (!to.isAfter(from)) {
      throw new ValidationException("El rango de fechas es inválido");
    }
    if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
      throw new ValidationException("El rango máximo es de 93 días");
    }
    return statistics.aggregate(from, to, zoneId, granularity);
  }
}

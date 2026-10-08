package com.parkingwatch.backend.statistics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tendencias por minuto, hora o día (PB-12). */
@RestController
@PreAuthorize("hasRole('OPERATOR')")
@Tag(name = "Estadísticas")
public class StatisticsController {

  private final StatisticsService statistics;

  public StatisticsController(StatisticsService statistics) {
    this.statistics = statistics;
  }

  @Operation(summary = "Agregados por minuto, hora o día (horas con más parqueo indebido)")
  @GetMapping("/api/v1/statistics")
  public List<StatisticsPoint> query(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) Long zoneId,
      @RequestParam(defaultValue = "HOUR") Granularity granularity) {
    return statistics.query(from, to, zoneId, granularity);
  }
}

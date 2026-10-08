package com.parkingwatch.backend.statistics;

import com.parkingwatch.common.contract.MinuteStatisticsMessage;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Agregados por minuto con SQL nativo de PostgreSQL: inserción idempotente (ON CONFLICT) y
 * agrupación con date_trunc en la zona horaria de Colombia.
 */
@Repository
public class StatisticsRepository {

  private static final String UPSERT =
      """
      INSERT INTO minute_statistics (minute, zone_id, vehicles_detected, occupancy_pct,
                                     avg_dwell_seconds, fps, avg_confidence)
      VALUES (:minute, :zoneId, :vehicles, :occupancy, :dwell, :fps, :confidence)
      ON CONFLICT (minute, zone_id) DO UPDATE
         SET vehicles_detected = EXCLUDED.vehicles_detected,
             occupancy_pct = EXCLUDED.occupancy_pct,
             avg_dwell_seconds = EXCLUDED.avg_dwell_seconds,
             fps = EXCLUDED.fps, avg_confidence = EXCLUDED.avg_confidence
      """;

  private static final String AGGREGATE =
      """
      SELECT date_trunc(:unit, minute, 'America/Bogota') AS bucket, zone_id,
             SUM(vehicles_detected) AS vehicles_detected,
             ROUND(AVG(occupancy_pct), 2) AS occupancy_pct,
             ROUND(AVG(avg_dwell_seconds)) AS avg_dwell_seconds,
             ROUND(AVG(fps), 2) AS fps,
             ROUND(AVG(avg_confidence), 2) AS avg_confidence
        FROM minute_statistics
       WHERE minute >= :from AND minute < :to
         AND (CAST(:zoneId AS BIGINT) IS NULL OR zone_id = :zoneId)
       GROUP BY 1, 2
       ORDER BY 1, 2
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public StatisticsRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Inserta o actualiza los agregados de un minuto. */
  public void upsert(Instant minute, List<MinuteStatisticsMessage.ZoneMinute> zones) {
    MapSqlParameterSource[] batch =
        zones.stream()
            .map(
                zone ->
                    new MapSqlParameterSource()
                        .addValue("minute", Timestamp.from(minute))
                        .addValue("zoneId", zone.zoneId())
                        .addValue("vehicles", zone.vehiclesDetected())
                        .addValue("occupancy", zone.occupancyPct())
                        .addValue("dwell", zone.avgDwellSeconds())
                        .addValue("fps", zone.fps())
                        .addValue("confidence", zone.avgConfidence()))
            .toArray(MapSqlParameterSource[]::new);
    jdbc.batchUpdate(UPSERT, batch);
  }

  /** Serie agregada por la granularidad pedida. */
  public List<StatisticsPoint> aggregate(
      Instant from, Instant to, Long zoneId, Granularity granularity) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("unit", granularity.truncUnit())
            .addValue("from", Timestamp.from(from))
            .addValue("to", Timestamp.from(to))
            .addValue("zoneId", zoneId, Types.BIGINT);
    return jdbc.query(AGGREGATE, params, (rs, row) -> map(rs));
  }

  private static StatisticsPoint map(ResultSet rs) throws SQLException {
    BigDecimal dwell = rs.getBigDecimal("avg_dwell_seconds");
    return new StatisticsPoint(
        rs.getTimestamp("bucket").toInstant(),
        rs.getLong("zone_id"),
        rs.getLong("vehicles_detected"),
        rs.getDouble("occupancy_pct"),
        dwell == null ? null : dwell.doubleValue(),
        rs.getDouble("fps"),
        rs.getDouble("avg_confidence"));
  }
}

package com.parkingwatch.backend.report;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio de reportes con consultas por filtros (Specification). */
public interface ReportRepository
    extends JpaRepository<Report, UUID>, JpaSpecificationExecutor<Report> {

  /** Reporte de una permanencia: un vehículo en una zona desde una hora de entrada (RF-7.2). */
  Optional<Report> findByCameraIdAndZoneIdAndTrackIdAndEnteredAt(
      String cameraId, long zoneId, long trackId, Instant enteredAt);

  boolean existsBySourceEventId(UUID sourceEventId);

  /** Cámaras con reportes nuevos cuyo vehículo sigue en la zona (estado rojo en el mapa). */
  @Query("select distinct r.cameraId from Report r where r.status = 'NEW' and r.exitedAt is null")
  Set<String> findCameraIdsWithOpenReports();

  /** Revisados recientes y cuántos fueron descartados por error del sistema (RF-14.2). */
  @Query(
      value =
          """
          SELECT count(*) AS reviewed,
                 count(*) FILTER (WHERE dismissal_reason IN ('FALSE_POSITIVE', 'PLATE_MISREAD'))
                   AS dismissed
            FROM (SELECT dismissal_reason FROM reports WHERE status <> 'NEW'
                   ORDER BY reviewed_at DESC LIMIT :sampleSize) recent
          """,
      nativeQuery = true)
  ReviewOutcomes recentReviewOutcomes(@Param("sampleSize") int sampleSize);

  /** Descartados antes de la fecha de corte, para la retención de datos (RNF-2.2). */
  @Query(
      "select r from Report r where r.status = 'DISMISSED'"
          + " and coalesce(r.reviewedAt, r.generatedAt) < :cutoff order by r.generatedAt")
  List<Report> findDismissedBefore(@Param("cutoff") Instant cutoff, Pageable page);

  /** Proyección de la tasa de descartes. */
  interface ReviewOutcomes {
    long getReviewed();

    long getDismissed();
  }
}

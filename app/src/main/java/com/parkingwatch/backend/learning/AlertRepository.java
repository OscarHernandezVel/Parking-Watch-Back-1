package com.parkingwatch.backend.learning;

import com.parkingwatch.common.domain.AlertType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio de alertas. */
public interface AlertRepository extends JpaRepository<Alert, Long> {

  @Query(
      "select count(a) > 0 from Alert a where a.alertType = :type and a.acknowledgedAt is null"
          + " and ((:cameraId is null and a.cameraId is null) or a.cameraId = :cameraId)")
  boolean hasOpen(@Param("type") AlertType type, @Param("cameraId") String cameraId);

  long countByAlertTypeInAndAcknowledgedAtIsNull(Collection<AlertType> types);

  List<Alert> findByAcknowledgedAtIsNullOrderByCreatedAtDesc(Pageable page);

  List<Alert> findAllByOrderByCreatedAtDesc(Pageable page);
}

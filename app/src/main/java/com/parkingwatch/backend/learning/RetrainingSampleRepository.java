package com.parkingwatch.backend.learning;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de muestras de reentrenamiento. */
public interface RetrainingSampleRepository extends JpaRepository<RetrainingSample, Long> {

  boolean existsByReportId(UUID reportId);

  long countByConsumedAtIsNull();
}

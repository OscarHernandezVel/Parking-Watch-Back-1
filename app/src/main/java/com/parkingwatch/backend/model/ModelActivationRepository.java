package com.parkingwatch.backend.model;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Historial de activaciones del modelo. */
public interface ModelActivationRepository extends JpaRepository<ModelActivation, Long> {

  /** Activaciones más recientes primero, para encontrar la versión anterior. */
  @Query("select a from ModelActivation a order by a.activatedAt desc, a.id desc")
  List<ModelActivation> findLatest(Pageable page);
}

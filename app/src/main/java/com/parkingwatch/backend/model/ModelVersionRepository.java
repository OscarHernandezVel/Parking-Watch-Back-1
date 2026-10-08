package com.parkingwatch.backend.model;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de versiones del modelo. */
public interface ModelVersionRepository extends JpaRepository<ModelVersion, String> {

  Optional<ModelVersion> findByActiveTrue();

  List<ModelVersion> findAllByOrderByTrainedAtDesc();
}

package com.parkingwatch.backend.ingestion;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Registro de eventos procesados. */
public interface IngestedEventRepository extends JpaRepository<IngestedEvent, UUID> {}

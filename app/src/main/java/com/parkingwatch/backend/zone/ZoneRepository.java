package com.parkingwatch.backend.zone;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de zonas no autorizadas. */
public interface ZoneRepository extends JpaRepository<NoParkingZone, Long> {

  List<NoParkingZone> findByCameraIdOrderByIdAsc(String cameraId);

  List<NoParkingZone> findByCameraIdAndActiveTrueOrderByIdAsc(String cameraId);
}

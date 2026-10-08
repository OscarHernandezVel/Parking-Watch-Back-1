package com.parkingwatch.backend.camera;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de cámaras. */
public interface CameraRepository extends JpaRepository<Camera, String> {

  Optional<Camera> findByDeviceTokenHash(String deviceTokenHash);

  List<Camera> findAllByOrderByIdAsc();

  List<Camera> findByStatus(CameraStatus status);
}

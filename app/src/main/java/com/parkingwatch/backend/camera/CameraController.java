package com.parkingwatch.backend.camera;

import com.parkingwatch.backend.live.LiveStateService;
import com.parkingwatch.backend.live.LiveView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cámaras con su estado y la vista en vivo (PB-01, PB-02). */
@RestController
@RequestMapping("/api/v1/cameras")
@PreAuthorize("hasRole('OPERATOR')")
@Tag(name = "Cámaras")
public class CameraController {

  private final CameraService cameras;
  private final LiveStateService live;

  public CameraController(CameraService cameras, LiveStateService live) {
    this.cameras = cameras;
    this.live = live;
  }

  @Operation(summary = "Cámaras con ubicación, estado y dirección del video (PB-01)")
  @GetMapping
  public List<CameraView> list() {
    return cameras.list();
  }

  @Operation(summary = "Detalle de una cámara")
  @GetMapping("/{cameraId}")
  public CameraView get(@PathVariable String cameraId) {
    return cameras.get(cameraId);
  }

  @Operation(summary = "Detecciones e indicadores en vivo (PB-02, PB-12)")
  @GetMapping("/{cameraId}/live")
  public LiveView live(@PathVariable String cameraId) {
    return live.get(cameraId);
  }
}

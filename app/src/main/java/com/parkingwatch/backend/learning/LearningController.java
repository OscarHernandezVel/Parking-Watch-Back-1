package com.parkingwatch.backend.learning;

import com.parkingwatch.backend.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Alertas de deriva y estado del reentrenamiento (PB-14), solo para el administrador. */
@RestController
@PreAuthorize("hasRole('ADMINISTRATOR')")
@Tag(name = "Aprendizaje continuo")
public class LearningController {

  private final AlertService alerts;
  private final RetrainingService retraining;

  public LearningController(AlertService alerts, RetrainingService retraining) {
    this.alerts = alerts;
    this.retraining = retraining;
  }

  @Operation(summary = "Alertas de deriva y de reentrenamiento")
  @GetMapping("/api/v1/alerts")
  public List<AlertView> alerts(@RequestParam(defaultValue = "true") boolean openOnly) {
    return alerts.list(openOnly);
  }

  @Operation(summary = "Marca una alerta como atendida")
  @PostMapping("/api/v1/alerts/{id}/acknowledge")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void acknowledge(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
    alerts.acknowledge(id, AuthenticatedUser.from(jwt).id());
  }

  @Operation(summary = "Muestras acumuladas y necesidad de reentrenar (RF-14.3)")
  @GetMapping("/api/v1/retraining/status")
  public RetrainingService.RetrainingStatus retrainingStatus() {
    return retraining.status();
  }
}

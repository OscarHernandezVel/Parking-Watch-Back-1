package com.parkingwatch.backend.shared;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** El modelo no cumple las métricas mínimas para desplegarse (RF-13.2). */
public class QualityGateException extends DomainException {

  private static final long serialVersionUID = 1L;

  public QualityGateException(String version, List<String> failures) {
    super(
        "La versión " + version + " no cumple las métricas mínimas",
        Map.of("version", version, "failures", List.copyOf(failures)));
  }

  @Override
  public String code() {
    return "QUALITY_GATE_FAILED";
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.UNPROCESSABLE_ENTITY;
  }
}

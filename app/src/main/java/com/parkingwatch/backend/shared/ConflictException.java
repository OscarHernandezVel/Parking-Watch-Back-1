package com.parkingwatch.backend.shared;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** La operación entra en conflicto con el estado actual del recurso. */
public class ConflictException extends DomainException {

  private static final long serialVersionUID = 1L;

  public ConflictException(String message) {
    super(message, Map.of());
  }

  protected ConflictException(String message, Map<String, Object> details) {
    super(message, details);
  }

  @Override
  public String code() {
    return "CONFLICT";
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.CONFLICT;
  }
}

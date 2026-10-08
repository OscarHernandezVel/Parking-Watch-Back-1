package com.parkingwatch.backend.shared;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Los datos no cumplen una regla de negocio. */
public class ValidationException extends DomainException {

  private static final long serialVersionUID = 1L;

  public ValidationException(String message) {
    super(message, Map.of());
  }

  @Override
  public String code() {
    return "VALIDATION_ERROR";
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }
}

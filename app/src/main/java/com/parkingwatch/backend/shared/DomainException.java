package com.parkingwatch.backend.shared;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Raíz de los errores de negocio. Cada subclase define un código estable y el estado HTTP con el
 * que se traduce a un documento application/problem+json (RFC 9457).
 */
public abstract class DomainException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient Map<String, Object> details;

  protected DomainException(String message, Map<String, Object> details) {
    super(message);
    this.details = Map.copyOf(details);
  }

  /** Código estable del error, independiente del mensaje. */
  public abstract String code();

  /** Estado HTTP con el que se responde. */
  public abstract HttpStatus status();

  public Map<String, Object> details() {
    return details;
  }
}

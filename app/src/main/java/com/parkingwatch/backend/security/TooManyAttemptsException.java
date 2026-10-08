package com.parkingwatch.backend.security;

import com.parkingwatch.backend.shared.DomainException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Usuario bloqueado temporalmente por demasiados intentos fallidos (HTTP 429). */
public class TooManyAttemptsException extends DomainException {

  private static final long serialVersionUID = 1L;

  public TooManyAttemptsException(Instant lockedUntil) {
    super(
        "Demasiados intentos fallidos. Intente de nuevo más tarde.",
        Map.of("lockedUntil", lockedUntil.toString()));
  }

  @Override
  public String code() {
    return "TOO_MANY_ATTEMPTS";
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.TOO_MANY_REQUESTS;
  }
}

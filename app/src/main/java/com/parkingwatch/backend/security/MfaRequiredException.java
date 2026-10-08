package com.parkingwatch.backend.security;

import com.parkingwatch.backend.shared.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * El administrador tiene segundo factor y no envió el código: la página muestra el campo del código
 * y repite el inicio de sesión (RNF-4.1).
 */
public class MfaRequiredException extends DomainException {

  private static final long serialVersionUID = 1L;

  public MfaRequiredException() {
    super("Ingrese el código de su aplicación de autenticación", Map.of());
  }

  @Override
  public String code() {
    return "MFA_REQUIRED";
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.UNAUTHORIZED;
  }
}

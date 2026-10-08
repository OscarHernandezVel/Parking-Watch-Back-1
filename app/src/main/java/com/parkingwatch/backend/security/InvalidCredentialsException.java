package com.parkingwatch.backend.security;

import com.parkingwatch.backend.shared.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Credenciales inválidas; el mensaje no revela si falló el usuario o la contraseña. */
public class InvalidCredentialsException extends DomainException {

  private static final long serialVersionUID = 1L;

  public InvalidCredentialsException() {
    super("Usuario o contraseña incorrectos", Map.of());
  }

  @Override
  public String code() {
    return "UNAUTHORIZED";
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.UNAUTHORIZED;
  }
}

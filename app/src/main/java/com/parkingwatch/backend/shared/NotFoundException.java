package com.parkingwatch.backend.shared;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** El recurso solicitado no existe. */
public class NotFoundException extends DomainException {

  private static final long serialVersionUID = 1L;

  public NotFoundException(String resource, Object id) {
    super(
        resource + " '" + id + "' no existe",
        Map.of("resource", resource, "id", String.valueOf(id)));
  }

  @Override
  public String code() {
    return "NOT_FOUND";
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.NOT_FOUND;
  }
}

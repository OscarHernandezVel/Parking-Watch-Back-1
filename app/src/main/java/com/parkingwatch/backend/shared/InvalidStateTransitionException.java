package com.parkingwatch.backend.shared;

import java.util.Map;

/** Transición de estado no permitida (por ejemplo, revisar dos veces un reporte). */
public class InvalidStateTransitionException extends ConflictException {

  private static final long serialVersionUID = 1L;

  public InvalidStateTransitionException(String from, String action) {
    super(
        "No se puede ejecutar '" + action + "' sobre un reporte en estado " + from,
        Map.of("from", from, "action", action));
  }

  @Override
  public String code() {
    return "INVALID_STATE_TRANSITION";
  }
}

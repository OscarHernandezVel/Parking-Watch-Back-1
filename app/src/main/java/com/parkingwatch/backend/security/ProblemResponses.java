package com.parkingwatch.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkingwatch.backend.shared.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** Escribe errores de seguridad como application/problem+json desde los filtros. */
public final class ProblemResponses {

  private ProblemResponses() {}

  /** Escribe el problema en la respuesta. */
  public static void write(
      ObjectMapper mapper,
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String code,
      String detail)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    mapper.writeValue(
        response.getOutputStream(), GlobalExceptionHandler.problem(status, code, detail, request));
  }
}

package com.parkingwatch.backend.shared;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce los errores a application/problem+json (RFC 9457) y oculta los detalles internos de los
 * errores inesperados (OWASP A05).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(DomainException.class)
  ProblemDetail handleDomain(DomainException error, HttpServletRequest request) {
    ProblemDetail problem = problem(error.status(), error.code(), error.getMessage(), request);
    if (!error.details().isEmpty()) {
      problem.setProperty("details", error.details());
    }
    return problem;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail handleInvalidBody(
      MethodArgumentNotValidException error, HttpServletRequest request) {
    List<Map<String, String>> issues =
        error.getBindingResult().getAllErrors().stream()
            .map(
                issue ->
                    Map.of(
                        "path",
                        issue instanceof FieldError field
                            ? field.getField()
                            : issue.getObjectName(),
                        "message",
                        String.valueOf(issue.getDefaultMessage())))
            .toList();
    return validation(request, issues);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ProblemDetail handleConstraint(ConstraintViolationException error, HttpServletRequest request) {
    List<Map<String, String>> issues =
        error.getConstraintViolations().stream()
            .map(v -> Map.of("path", v.getPropertyPath().toString(), "message", v.getMessage()))
            .toList();
    return validation(request, issues);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ProblemDetail handleUnreadable(Exception error, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La solicitud no cumple el contrato", request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail handleForbidden(AccessDeniedException error, HttpServletRequest request) {
    return problem(
        HttpStatus.FORBIDDEN, "FORBIDDEN", "No tiene permisos para esta operación", request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ProblemDetail handleNoResource(NoResourceFoundException error, HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Ruta no encontrada", request);
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception error, HttpServletRequest request) {
    LOG.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), error);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "Ocurrió un error inesperado. Consulte los logs del servicio.",
        request);
  }

  private static ProblemDetail validation(
      HttpServletRequest request, List<Map<String, String>> issues) {
    ProblemDetail problem =
        problem(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "La solicitud no cumple el contrato",
            request);
    problem.setProperty("details", Map.of("issues", issues));
    return problem;
  }

  /** Construye un problema con tipo, código estable e instancia. */
  public static ProblemDetail problem(
      HttpStatus status, String code, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("https://parking-watch/errors/" + code.toLowerCase(Locale.ROOT)));
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", code);
    return problem;
  }
}

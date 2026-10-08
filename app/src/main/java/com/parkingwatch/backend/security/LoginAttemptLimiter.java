package com.parkingwatch.backend.security;

import com.parkingwatch.backend.config.ParkingProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Límite de intentos de inicio de sesión (RNF-4.1, OWASP A07): tras N fallos seguidos de un mismo
 * usuario, se bloquea durante un tiempo. El estado vive en memoria porque el servicio corre como
 * una sola instancia en Render.
 */
@Component
public class LoginAttemptLimiter {

  private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
  private final Clock clock;
  private final int maxAttempts;
  private final Duration lockDuration;

  public LoginAttemptLimiter(Clock clock, ParkingProperties properties) {
    this.clock = clock;
    this.maxAttempts = properties.security().loginMaxAttempts();
    this.lockDuration = properties.security().loginLockDuration();
  }

  private record Attempts(int failures, Instant lockedUntil) {}

  /** Lanza {@link TooManyAttemptsException} si el usuario está bloqueado. */
  public void checkAllowed(String username) {
    Attempts current = attempts.get(username);
    if (current != null
        && current.lockedUntil() != null
        && clock.instant().isBefore(current.lockedUntil())) {
      throw new TooManyAttemptsException(current.lockedUntil());
    }
  }

  /** Registra un fallo; al llegar al máximo bloquea al usuario. */
  public void recordFailure(String username) {
    Instant now = clock.instant();
    attempts.compute(
        username,
        (key, current) -> {
          int failures = current == null || isExpired(current, now) ? 1 : current.failures() + 1;
          Instant lockedUntil = failures >= maxAttempts ? now.plus(lockDuration) : null;
          return new Attempts(failures, lockedUntil);
        });
  }

  /** Limpia el contador tras un inicio de sesión correcto. */
  public void recordSuccess(String username) {
    attempts.remove(username);
  }

  private static boolean isExpired(Attempts current, Instant now) {
    return current.lockedUntil() != null && !now.isBefore(current.lockedUntil());
  }
}

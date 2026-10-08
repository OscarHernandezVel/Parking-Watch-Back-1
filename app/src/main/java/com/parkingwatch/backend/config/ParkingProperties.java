package com.parkingwatch.backend.config;

import com.parkingwatch.common.contract.EdgeConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración del sistema leída de variables de entorno de Render (RNF-11.2), validada al
 * arrancar. Ningún valor por entorno queda escrito en el código.
 *
 * @param security sesiones, CORS, segundo factor y límite de intentos
 * @param storage disco persistente de fotos y modelos
 * @param devices tokens de los dispositivos Edge
 * @param rules parámetros del motor de reglas
 * @param retention retención de datos personales
 * @param learning aprendizaje continuo y deriva
 * @param realtime canal WebSocket con el Edge y los navegadores
 * @param edge intervalos de comunicación y video que se entregan al Edge
 */
@Validated
@ConfigurationProperties(prefix = "parking")
public record ParkingProperties(
    @Valid @NotNull Security security,
    @Valid @NotNull Storage storage,
    @Valid @NotNull Devices devices,
    @Valid @NotNull Rules rules,
    @Valid @NotNull Retention retention,
    @Valid @NotNull Learning learning,
    @Valid @NotNull Realtime realtime,
    @NotNull EdgeConfiguration.Settings edge) {

  /**
   * Firma de sesiones JWT de corta duración, orígenes permitidos (dominio de Vercel), segundo
   * factor para administradores y límite de intentos de inicio de sesión (RNF-4.1).
   */
  public record Security(
      @NotBlank @Size(min = 32) String jwtSecret,
      @NotNull Duration jwtTtl,
      List<String> corsOrigins,
      boolean adminMfaRequired,
      @Positive int loginMaxAttempts,
      @NotNull Duration loginLockDuration) {

    /** Copia defensiva de los orígenes. */
    public Security {
      corsOrigins = corsOrigins == null ? List.of() : List.copyOf(corsOrigins);
    }
  }

  /** Carpeta del disco persistente de Render ({@code STORAGE_PATH=/data}). */
  public record Storage(@NotBlank String path) {}

  /**
   * Tokens de los dispositivos Edge ({@code EDGE_DEVICE_TOKENS}), con el formato {@code
   * CAM-MAQ-01=pwd_...,CAM-02=pwd_...}. Solo su hash SHA-256 se guarda en la base de datos.
   */
  public record Devices(String tokens) {}

  /** Parámetros del motor de reglas (PB-07). */
  public record Rules(
      @DecimalMin("0.0") @DecimalMax("1.0") double minDetectionConfidence,
      @NotNull Duration heartbeatTimeout) {}

  /** Retención de reportes descartados (RNF-4.2). */
  public record Retention(@Positive int dismissedReportDays) {}

  /** Umbrales del aprendizaje continuo (PB-16). */
  public record Learning(
      @Positive int retrainingSampleThreshold,
      @DecimalMin("0.0") @DecimalMax("1.0") double dismissalRateThreshold,
      @Positive int dismissalSampleSize,
      @Positive int dismissalMinimumSample) {}

  /**
   * Canal en tiempo real (PB-08).
   *
   * @param maxMessageBytes tamaño máximo de un mensaje STOMP (fotogramas JPEG incluidos)
   * @param sendTimeLimit tiempo máximo de envío a un navegador antes de cerrar su sesión
   * @param sendBufferBytes memoria máxima en espera por sesión
   */
  public record Realtime(
      @Positive int maxMessageBytes,
      @NotNull Duration sendTimeLimit,
      @Positive int sendBufferBytes) {}
}

package com.parkingwatch.common.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Señal de vida del agente cada pocos segundos (RF-6.3). */
public record HeartbeatMessage(
    @NotNull Instant sentAt,
    @PositiveOrZero double fps,
    @NotBlank @Size(max = 20) String modelVersion,
    @PositiveOrZero long uptimeSeconds,
    @NotBlank @Size(max = 30) String agentVersion) {}

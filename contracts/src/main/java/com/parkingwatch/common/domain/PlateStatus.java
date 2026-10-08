package com.parkingwatch.common.domain;

/** Estado de la lectura de placa tras la votación temporal (RF-11.2). */
public enum PlateStatus {
  READ,
  PENDING_CONFIRMATION,
  NOT_READ
}

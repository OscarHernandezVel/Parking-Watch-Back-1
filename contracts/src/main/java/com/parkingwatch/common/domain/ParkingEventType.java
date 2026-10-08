package com.parkingwatch.common.domain;

/** Eventos de cambio que el agente publica al backend (RF-9.4). */
public enum ParkingEventType {
  ZONE_ENTERED,
  TOLERANCE_EXCEEDED,
  ZONE_EXITED
}

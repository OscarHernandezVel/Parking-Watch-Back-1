package com.parkingwatch.backend.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj controlable para probar tolerancias, sesiones y retención de forma determinista. */
public final class MutableClock extends Clock {

  private volatile Instant now;

  public MutableClock(Instant start) {
    this.now = start;
  }

  public void advance(Duration duration) {
    now = now.plus(duration);
  }

  public void set(Instant instant) {
    now = instant;
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    return now;
  }
}

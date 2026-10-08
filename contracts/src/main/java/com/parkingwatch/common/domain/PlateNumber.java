package com.parkingwatch.common.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Validación de placas colombianas: ABC123 (carro) y ABC12D (moto) (RF-11.1). */
public final class PlateNumber {

  private static final Pattern CAR = Pattern.compile("^[A-Z]{3}\\d{3}$");
  private static final Pattern MOTORCYCLE = Pattern.compile("^[A-Z]{3}\\d{2}[A-Z]$");
  private static final Pattern SEPARATORS = Pattern.compile("[\\s-]");

  private PlateNumber() {}

  /** Normaliza una lectura y la retorna solo si tiene un formato colombiano válido. */
  public static Optional<String> normalize(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String value = SEPARATORS.matcher(raw.toUpperCase(Locale.ROOT)).replaceAll("");
    return isCar(value) || isMotorcycle(value) ? Optional.of(value) : Optional.empty();
  }

  /** Indica si el formato de la placa corresponde al tipo de vehículo detectado. */
  public static boolean isConsistentWith(String plate, VehicleType type) {
    return type == VehicleType.MOTORCYCLE ? isMotorcycle(plate) : isCar(plate);
  }

  private static boolean isCar(String value) {
    return CAR.matcher(value).matches();
  }

  private static boolean isMotorcycle(String value) {
    return MOTORCYCLE.matcher(value).matches();
  }
}

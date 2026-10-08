package com.parkingwatch.common.contract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Ejemplos JSON del contrato incluidos en el paquete cupo-contracts ({@code contracts/examples}).
 * Las pruebas de contrato del backend y del Edge leen los mismos archivos (RNF-11.1).
 */
public final class ContractExamples {

  private static final String FOLDER = "/contracts/examples/";

  private ContractExamples() {}

  /** Lee el ejemplo {@code name}.json del classpath. */
  public static String read(String name) throws IOException {
    try (InputStream input = ContractExamples.class.getResourceAsStream(FOLDER + name + ".json")) {
      if (input == null) {
        throw new IOException("No existe el ejemplo de contrato " + name);
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}

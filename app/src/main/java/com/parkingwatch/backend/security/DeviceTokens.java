package com.parkingwatch.backend.security;

import com.parkingwatch.common.contract.EdgeApi;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Tokens de los agentes de borde: solo se guarda su hash SHA-256 en la base de datos. */
public final class DeviceTokens {

  private static final SecureRandom RANDOM = new SecureRandom();

  private DeviceTokens() {}

  /** Genera un token aleatorio de 256 bits con prefijo {@code pwd_}. */
  public static String generate() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return EdgeApi.DEVICE_TOKEN_PREFIX
        + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Hash SHA-256 en hexadecimal del token. */
  public static String hash(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no disponible", e);
    }
  }
}

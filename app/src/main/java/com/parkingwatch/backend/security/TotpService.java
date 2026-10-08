package com.parkingwatch.backend.security;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Segundo factor para administradores (RNF-4.1): códigos de un solo uso basados en tiempo (TOTP,
 * RFC 6238) de 6 dígitos cada 30 s, compatibles con las aplicaciones de autenticación habituales.
 * Acepta un intervalo de diferencia para tolerar relojes desfasados.
 */
@Service
public class TotpService {

  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  private static final int SECRET_BYTES = 20;
  private static final long STEP_SECONDS = 30;
  private static final int DIGITS = 6;
  private static final int MODULO = 1_000_000;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String ISSUER = "Cupo Transito";

  private final Clock clock;

  public TotpService(Clock clock) {
    this.clock = clock;
  }

  /** Genera un secreto aleatorio de 160 bits codificado en Base32. */
  public String newSecret() {
    byte[] bytes = new byte[SECRET_BYTES];
    RANDOM.nextBytes(bytes);
    return base32(bytes);
  }

  /** URI otpauth para registrar el secreto con un código QR. */
  public String provisioningUri(String username, String secret) {
    String label = URLEncoder.encode(ISSUER + ":" + username, StandardCharsets.UTF_8);
    return "otpauth://totp/"
        + label
        + "?secret="
        + secret
        + "&issuer="
        + URLEncoder.encode(ISSUER, StandardCharsets.UTF_8)
        + "&digits="
        + DIGITS
        + "&period="
        + STEP_SECONDS;
  }

  /** Verifica un código contra el intervalo actual y los dos vecinos. */
  public boolean verify(String secret, String code) {
    if (secret == null || code == null || !code.matches("[0-9]{6}")) {
      return false;
    }
    long step = clock.instant().getEpochSecond() / STEP_SECONDS;
    int expected = Integer.parseInt(code);
    for (long offset = -1; offset <= 1; offset++) {
      if (codeAt(secret, step + offset) == expected) {
        return true;
      }
    }
    return false;
  }

  /** Código vigente (lo usan las pruebas). */
  public String currentCode(String secret) {
    long step = clock.instant().getEpochSecond() / STEP_SECONDS;
    return String.format(Locale.ROOT, "%06d", codeAt(secret, step));
  }

  private static int codeAt(String secret, long step) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(decodeBase32(secret), "HmacSHA1"));
      byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
      int offset = hash[hash.length - 1] & 0x0F;
      int binary =
          ((hash[offset] & 0x7F) << 24)
              | ((hash[offset + 1] & 0xFF) << 16)
              | ((hash[offset + 2] & 0xFF) << 8)
              | (hash[offset + 3] & 0xFF);
      return binary % MODULO;
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HmacSHA1 no disponible", e);
    }
  }

  static String base32(byte[] bytes) {
    StringBuilder out = new StringBuilder();
    int buffer = 0;
    int bits = 0;
    for (byte value : bytes) {
      buffer = (buffer << 8) | (value & 0xFF);
      bits += 8;
      while (bits >= 5) {
        out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1F));
        bits -= 5;
      }
    }
    if (bits > 0) {
      out.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1F));
    }
    return out.toString();
  }

  static byte[] decodeBase32(String text) {
    String clean = text.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
    ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
    int buffer = 0;
    int bits = 0;
    for (char symbol : clean.toCharArray()) {
      int value = ALPHABET.indexOf(symbol);
      if (value < 0) {
        throw new IllegalArgumentException("Secreto TOTP inválido");
      }
      buffer = (buffer << 5) | value;
      bits += 5;
      if (bits >= 8) {
        out.put((byte) ((buffer >> (bits - 8)) & 0xFF));
        bits -= 8;
      }
    }
    return out.array();
  }
}

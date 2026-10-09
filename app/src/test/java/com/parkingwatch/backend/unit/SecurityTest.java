package com.parkingwatch.backend.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parkingwatch.backend.camera.DeviceTokenSynchronizer;
import com.parkingwatch.backend.config.ParkingProperties;
import com.parkingwatch.backend.security.JwtAuthorities;
import com.parkingwatch.backend.security.LoginAttemptLimiter;
import com.parkingwatch.backend.security.TooManyAttemptsException;
import com.parkingwatch.backend.security.TotpService;
import com.parkingwatch.backend.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Segundo factor, límite de intentos, permisos del JWT y tokens de dispositivo (RNF-4.1). */
class SecurityTest {

  private static final Instant T0 = Instant.parse("2026-10-05T15:00:00Z");

  @Test
  void totpCodesFollowRfc6238AndTolerateOneStep() {
    MutableClock clock = new MutableClock(Instant.ofEpochSecond(59));
    TotpService totp = new TotpService(clock);
    // Vector de prueba del RFC 6238 (SHA-1, T = 59 s): 94287082 -> 6 dígitos 287082.
    String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    assertThat(totp.currentCode(secret)).isEqualTo("287082");
    assertThat(totp.verify(secret, "287082")).isTrue();
    clock.advance(Duration.ofSeconds(30));
    assertThat(totp.verify(secret, "287082")).isTrue();
    clock.advance(Duration.ofSeconds(60));
    assertThat(totp.verify(secret, "287082")).isFalse();
    assertThat(totp.verify(secret, "12ab56")).isFalse();
    assertThat(totp.verify(null, "123456")).isFalse();
    String generated = totp.newSecret();
    assertThat(generated).hasSize(32).matches("[A-Z2-7]+");
    assertThat(totp.verify(generated, totp.currentCode(generated))).isTrue();
    assertThat(totp.provisioningUri("admin", generated))
        .startsWith("otpauth://totp/Cupo+Transito%3Aadmin?secret=" + generated);
    assertThatThrownBy(() -> totp.currentCode("no-base32!"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void loginAttemptsAreLimitedAndUnlockedAfterTheLockTime() {
    MutableClock clock = new MutableClock(T0);
    LoginAttemptLimiter limiter =
        new LoginAttemptLimiter(clock, RulesAndLearningTest.properties(2));
    for (int i = 0; i < 4; i++) {
      limiter.recordFailure("ana");
    }
    limiter.checkAllowed("ana");
    limiter.recordFailure("ana");
    assertThatThrownBy(() -> limiter.checkAllowed("ana"))
        .isInstanceOf(TooManyAttemptsException.class);
    limiter.checkAllowed("otro");
    clock.advance(Duration.ofMinutes(16));
    limiter.checkAllowed("ana");
    limiter.recordFailure("ana");
    limiter.checkAllowed("ana");
    limiter.recordSuccess("ana");
    limiter.checkAllowed("ana");
  }

  @Test
  void administratorsWithoutSecondFactorOnlyGetOperatorPermissions() {
    JwtAuthorities strict = new JwtAuthorities(true);
    assertThat(names(strict, "ADMINISTRATOR", false))
        .containsExactlyInAnyOrder("ROLE_OPERATOR", JwtAuthorities.MFA_ENROLLMENT);
    assertThat(names(strict, "ADMINISTRATOR", true)).containsExactly("ROLE_ADMINISTRATOR");
    assertThat(names(strict, "OPERATOR", false)).containsExactly("ROLE_OPERATOR");
    assertThat(names(new JwtAuthorities(false), "ADMINISTRATOR", false))
        .containsExactly("ROLE_ADMINISTRATOR");
  }

  @Test
  void deviceTokensAreReadFromTheRenderVariable() {
    assertThat(DeviceTokenSynchronizer.parse(" CAM-1=pwd_a , CAM-2=pwd_b"))
        .containsExactly(Map.entry("CAM-1", "pwd_a"), Map.entry("CAM-2", "pwd_b"));
    assertThat(DeviceTokenSynchronizer.parse("")).isEmpty();
    assertThat(DeviceTokenSynchronizer.parse(null)).isEmpty();
    assertThatThrownBy(() -> DeviceTokenSynchronizer.parse("CAM-1=sinprefijo"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DeviceTokenSynchronizer.parse("CAM-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void securityPropertiesKeepADefensiveCopyOfTheOrigins() {
    ParkingProperties.Security security =
        new ParkingProperties.Security(
            "x".repeat(32), Duration.ofMinutes(30), null, true, 5, Duration.ofMinutes(15));
    assertThat(security.corsOrigins()).isEmpty();
  }

  private static java.util.List<String> names(JwtAuthorities converter, String role, boolean mfa) {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "HS256")
            .claim("role", role)
            .claim("mfa", mfa)
            .issuedAt(T0)
            .expiresAt(T0.plusSeconds(60))
            .build();
    return converter.convert(jwt).stream().map(GrantedAuthority::getAuthority).toList();
  }
}

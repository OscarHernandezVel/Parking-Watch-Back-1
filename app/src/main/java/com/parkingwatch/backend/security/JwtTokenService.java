package com.parkingwatch.backend.security;

import com.parkingwatch.backend.config.ParkingProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Emite sesiones sin estado firmadas con HS256 para la página web, la API y el WebSocket. */
@Service
public class JwtTokenService {

  public static final String ISSUER = "parking-watch";

  private final JwtEncoder encoder;
  private final Clock clock;
  private final ParkingProperties properties;

  public JwtTokenService(JwtEncoder encoder, Clock clock, ParkingProperties properties) {
    this.encoder = encoder;
    this.clock = clock;
    this.properties = properties;
  }

  /** Token emitido y su vencimiento. */
  public record IssuedToken(String accessToken, Instant expiresAt) {}

  /**
   * Emite un token de corta duración para el usuario autenticado.
   *
   * @param mfa true si la sesión se abrió con segundo factor
   */
  public IssuedToken issue(AuthenticatedUser user, boolean mfa) {
    Instant now = clock.instant();
    Instant expiresAt = now.plus(properties.security().jwtTtl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(user.id().toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim(AuthenticatedUser.CLAIM_USERNAME, user.username())
            .claim(AuthenticatedUser.CLAIM_NAME, user.fullName())
            .claim(AuthenticatedUser.CLAIM_ROLE, user.role().name())
            .claim(AuthenticatedUser.CLAIM_MFA, mfa)
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new IssuedToken(token, expiresAt);
  }
}

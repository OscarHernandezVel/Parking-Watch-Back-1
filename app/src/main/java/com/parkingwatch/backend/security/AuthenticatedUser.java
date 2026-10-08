package com.parkingwatch.backend.security;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Funcionario autenticado, reconstruido a partir de las afirmaciones del JWT. */
public record AuthenticatedUser(UUID id, String username, String fullName, Role role) {

  static final String CLAIM_USERNAME = "usr";
  static final String CLAIM_NAME = "name";
  static final String CLAIM_ROLE = "role";

  /** Afirmación que indica que la sesión se abrió con segundo factor (RNF-4.1). */
  public static final String CLAIM_MFA = "mfa";

  /** Lee el usuario desde el token validado por Spring Security. */
  public static AuthenticatedUser from(Jwt jwt) {
    return new AuthenticatedUser(
        UUID.fromString(jwt.getSubject()),
        jwt.getClaimAsString(CLAIM_USERNAME),
        jwt.getClaimAsString(CLAIM_NAME),
        Role.valueOf(jwt.getClaimAsString(CLAIM_ROLE)));
  }

  static AuthenticatedUser of(AppUser user) {
    return new AuthenticatedUser(
        user.getId(), user.getUsername(), user.getFullName(), user.getRole());
  }
}

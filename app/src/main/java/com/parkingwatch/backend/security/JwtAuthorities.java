package com.parkingwatch.backend.security;

import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Permisos de una sesión según su rol (RF-5.2) y el segundo factor (RNF-4.1). Un administrador que
 * no inició sesión con segundo factor solo obtiene los permisos de operador y el permiso de
 * registrar su segundo factor, hasta que lo active.
 */
public final class JwtAuthorities implements Converter<Jwt, Collection<GrantedAuthority>> {

  /** Permiso temporal para registrar el segundo factor. */
  public static final String MFA_ENROLLMENT = "MFA_ENROLLMENT";

  private final boolean adminMfaRequired;

  public JwtAuthorities(boolean adminMfaRequired) {
    this.adminMfaRequired = adminMfaRequired;
  }

  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    Role role = Role.valueOf(jwt.getClaimAsString(AuthenticatedUser.CLAIM_ROLE));
    boolean mfa = Boolean.TRUE.equals(jwt.getClaimAsBoolean(AuthenticatedUser.CLAIM_MFA));
    if (role == Role.ADMINISTRATOR && adminMfaRequired && !mfa) {
      return List.of(
          new SimpleGrantedAuthority("ROLE_" + Role.OPERATOR.name()),
          new SimpleGrantedAuthority(MFA_ENROLLMENT));
    }
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }
}

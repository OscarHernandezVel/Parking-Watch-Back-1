package com.parkingwatch.backend.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Funcionario con acceso a la plataforma (tabla users, PB-05). */
@Entity
@Table(name = "users")
public class AppUser {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 80)
  private String username;

  @Column(name = "full_name", nullable = false, length = 100)
  private String fullName;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private Role role;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "totp_secret", length = 64)
  private String totpSecret;

  @Column(name = "totp_enabled", nullable = false)
  private boolean totpEnabled;

  protected AppUser() {}

  /** Crea un usuario habilitado con el nombre normalizado en minúsculas. */
  public AppUser(String username, String fullName, String passwordHash, Role role, Instant now) {
    this.id = UUID.randomUUID();
    this.username = normalize(username);
    this.fullName = fullName.trim();
    this.passwordHash = passwordHash;
    this.role = role;
    this.enabled = true;
    this.createdAt = now;
  }

  /** Normalización de nombres de usuario para búsquedas sin distinguir mayúsculas. */
  public static String normalize(String username) {
    return username.trim().toLowerCase(Locale.ROOT);
  }

  public UUID getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getFullName() {
    return fullName;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public boolean isEnabled() {
    return enabled;
  }

  /** Guarda un secreto TOTP pendiente de confirmar (aún no se exige). */
  public void startTotpEnrollment(String secret) {
    this.totpSecret = secret;
    this.totpEnabled = false;
  }

  /** Activa el segundo factor con el secreto pendiente, ya confirmado con un código válido. */
  public void enableTotp() {
    if (totpSecret == null) {
      throw new IllegalStateException("No hay un secreto TOTP pendiente");
    }
    this.totpEnabled = true;
  }

  public String getTotpSecret() {
    return totpSecret;
  }

  public boolean isTotpEnabled() {
    return totpEnabled;
  }
}

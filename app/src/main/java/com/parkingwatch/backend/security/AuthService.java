package com.parkingwatch.backend.security;

import com.parkingwatch.backend.config.ParkingProperties;
import com.parkingwatch.backend.shared.ConflictException;
import com.parkingwatch.backend.shared.NotFoundException;
import com.parkingwatch.backend.shared.ValidationException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicio de sesión y administración de funcionarios (PB-05, RNF-4.1): contraseñas con bcrypt,
 * límite de intentos, JWT de corta duración y segundo factor (TOTP) para administradores.
 */
@Service
public class AuthService {

  private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService tokens;
  private final TotpService totp;
  private final LoginAttemptLimiter limiter;
  private final Clock clock;
  private final boolean adminMfaRequired;

  public AuthService(
      AppUserRepository users,
      PasswordEncoder passwordEncoder,
      JwtTokenService tokens,
      MfaCollaborators mfa,
      Clock clock,
      ParkingProperties properties) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokens = tokens;
    this.totp = mfa.totp();
    this.limiter = mfa.limiter();
    this.clock = clock;
    this.adminMfaRequired = properties.security().adminMfaRequired();
  }

  /** Colaboradores del segundo factor y del límite de intentos. */
  @Component
  public record MfaCollaborators(TotpService totp, LoginAttemptLimiter limiter) {}

  /**
   * Resultado del inicio de sesión.
   *
   * @param mfaSetupRequired el administrador debe registrar su segundo factor antes de usar las
   *     funciones de administración
   */
  public record LoginResult(
      JwtTokenService.IssuedToken token, AuthenticatedUser user, boolean mfaSetupRequired) {}

  /** Secreto TOTP pendiente de confirmar y su URI para el código QR. */
  public record MfaEnrollment(String secret, String otpauthUri) {}

  /** Valida las credenciales (y el código TOTP de los administradores) y emite la sesión. */
  @Transactional(readOnly = true)
  public LoginResult login(String username, String password, String otp) {
    String normalized = AppUser.normalize(username);
    limiter.checkAllowed(normalized);
    AppUser user =
        users
            .findByUsername(normalized)
            .filter(AppUser::isEnabled)
            .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
            .orElseThrow(() -> failure(normalized));
    boolean mfa = false;
    if (user.isTotpEnabled()) {
      if (otp == null || otp.isBlank()) {
        throw new MfaRequiredException();
      }
      if (!totp.verify(user.getTotpSecret(), otp.trim())) {
        throw failure(normalized);
      }
      mfa = true;
    }
    limiter.recordSuccess(normalized);
    return result(AuthenticatedUser.of(user), mfa);
  }

  /**
   * Renueva la sesión vigente con los mismos permisos (la página lo hace antes del vencimiento).
   */
  @Transactional(readOnly = true)
  public LoginResult refresh(UUID userId, boolean mfa) {
    AppUser user =
        users
            .findById(userId)
            .filter(AppUser::isEnabled)
            .orElseThrow(InvalidCredentialsException::new);
    return result(AuthenticatedUser.of(user), mfa && user.isTotpEnabled());
  }

  /** Genera un secreto TOTP para el administrador; se exige solo después de confirmarlo. */
  @Transactional
  public MfaEnrollment startMfaEnrollment(UUID userId) {
    AppUser user = requireAdministrator(userId);
    if (user.isTotpEnabled()) {
      throw new ConflictException("El segundo factor ya está activo");
    }
    String secret = totp.newSecret();
    user.startTotpEnrollment(secret);
    return new MfaEnrollment(secret, totp.provisioningUri(user.getUsername(), secret));
  }

  /**
   * Confirma el secreto con un código válido, activa el segundo factor y emite una sesión nueva.
   */
  @Transactional
  public LoginResult activateMfa(UUID userId, String code) {
    AppUser user = requireAdministrator(userId);
    if (user.getTotpSecret() == null || !totp.verify(user.getTotpSecret(), code)) {
      throw new ValidationException("El código no es válido");
    }
    user.enableTotp();
    LOG.info("Segundo factor activado para {}", user.getUsername());
    return result(AuthenticatedUser.of(user), true);
  }

  /** Crea un operador o administrador con la contraseña cifrada con bcrypt. */
  @Transactional
  public AuthenticatedUser createUser(
      String username, String fullName, String password, Role role) {
    if (users.existsByUsername(AppUser.normalize(username))) {
      throw new ConflictException("El usuario " + AppUser.normalize(username) + " ya existe");
    }
    AppUser user =
        users.save(
            new AppUser(
                username, fullName, passwordEncoder.encode(password), role, clock.instant()));
    return AuthenticatedUser.of(user);
  }

  /** Activa directamente el segundo factor con un secreto conocido (arranque de la maqueta). */
  @Transactional
  public void enableMfaWithSecret(String username, String secret) {
    users
        .findByUsername(AppUser.normalize(username))
        .ifPresent(
            user -> {
              user.startTotpEnrollment(secret);
              user.enableTotp();
            });
  }

  @Transactional(readOnly = true)
  public List<AuthenticatedUser> listUsers() {
    return users.findAllByOrderByUsernameAsc().stream().map(AuthenticatedUser::of).toList();
  }

  private LoginResult result(AuthenticatedUser user, boolean mfa) {
    boolean setupRequired = adminMfaRequired && user.role() == Role.ADMINISTRATOR && !mfa;
    return new LoginResult(tokens.issue(user, mfa), user, setupRequired);
  }

  private AppUser requireAdministrator(UUID userId) {
    AppUser user =
        users.findById(userId).orElseThrow(() -> new NotFoundException("Usuario", userId));
    if (user.getRole() != Role.ADMINISTRATOR) {
      throw new ValidationException("El segundo factor aplica solo a administradores");
    }
    return user;
  }

  private InvalidCredentialsException failure(String username) {
    limiter.recordFailure(username);
    LOG.warn("Inicio de sesión fallido para {}", username);
    return new InvalidCredentialsException();
  }
}

package com.parkingwatch.backend.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Inicio de sesión, renovación, segundo factor y gestión de usuarios (PB-05, RNF-4.1). */
@RestController
@Tag(name = "Acceso")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /** Credenciales; {@code otp} es el código TOTP de los administradores con segundo factor. */
  public record LoginRequest(
      @NotBlank @Size(max = 80) String username,
      @NotBlank @Size(max = 200) String password,
      @Pattern(regexp = "^[0-9]{6}$") String otp) {}

  /**
   * Sesión emitida. La página guarda el token solo en memoria (RNF-3.1) y lo renueva antes de
   * {@code expiresAt}. Con {@code mfaSetupRequired} el administrador debe registrar su segundo
   * factor antes de usar las funciones de administración.
   */
  public record LoginResponse(
      String accessToken,
      String tokenType,
      Instant expiresAt,
      AuthenticatedUser user,
      boolean mfaSetupRequired) {

    static LoginResponse of(AuthService.LoginResult result) {
      return new LoginResponse(
          result.token().accessToken(),
          "Bearer",
          result.token().expiresAt(),
          result.user(),
          result.mfaSetupRequired());
    }
  }

  /** Código TOTP para confirmar el registro del segundo factor. */
  public record MfaCode(@NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {}

  /** Datos de un nuevo usuario. */
  public record CreateUserRequest(
      @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,80}$") String username,
      @NotBlank @Size(min = 3, max = 100) String fullName,
      @NotBlank @Size(min = 12, max = 200) String password,
      @NotNull Role role) {}

  @Operation(summary = "Inicio de sesión de funcionarios (JWT de corta duración)")
  @PostMapping("/api/v1/auth/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return LoginResponse.of(
        authService.login(request.username(), request.password(), request.otp()));
  }

  @Operation(summary = "Renueva la sesión vigente antes de que venza")
  @PostMapping("/api/v1/auth/refresh")
  public LoginResponse refresh(@AuthenticationPrincipal Jwt jwt) {
    return LoginResponse.of(
        authService.refresh(
            AuthenticatedUser.from(jwt).id(),
            Boolean.TRUE.equals(jwt.getClaimAsBoolean(AuthenticatedUser.CLAIM_MFA))));
  }

  @Operation(summary = "Genera el secreto del segundo factor del administrador (código QR)")
  @PreAuthorize("hasAnyAuthority('ROLE_ADMINISTRATOR', 'MFA_ENROLLMENT')")
  @PostMapping("/api/v1/auth/mfa/setup")
  public AuthService.MfaEnrollment setupMfa(@AuthenticationPrincipal Jwt jwt) {
    return authService.startMfaEnrollment(AuthenticatedUser.from(jwt).id());
  }

  @Operation(summary = "Activa el segundo factor con un código válido y emite una sesión nueva")
  @PreAuthorize("hasAnyAuthority('ROLE_ADMINISTRATOR', 'MFA_ENROLLMENT')")
  @PostMapping("/api/v1/auth/mfa/activate")
  public LoginResponse activateMfa(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MfaCode request) {
    return LoginResponse.of(
        authService.activateMfa(AuthenticatedUser.from(jwt).id(), request.code()));
  }

  @Operation(summary = "Lista de usuarios (administrador)")
  @PreAuthorize("hasRole('ADMINISTRATOR')")
  @GetMapping("/api/v1/users")
  public List<AuthenticatedUser> users() {
    return authService.listUsers();
  }

  @Operation(summary = "Crea un operador o administrador (administrador)")
  @PreAuthorize("hasRole('ADMINISTRATOR')")
  @PostMapping("/api/v1/users")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthenticatedUser createUser(@Valid @RequestBody CreateUserRequest request) {
    return authService.createUser(
        request.username(), request.fullName(), request.password(), request.role());
  }
}

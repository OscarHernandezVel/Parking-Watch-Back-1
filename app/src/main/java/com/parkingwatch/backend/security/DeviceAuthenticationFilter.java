package com.parkingwatch.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkingwatch.common.contract.EdgeApi;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica al agente de borde con su token de dispositivo y exige que la cámara de la ruta sea la
 * suya: un dispositivo nunca puede escribir datos de otra cámara (OWASP A01).
 */
public class DeviceAuthenticationFilter extends OncePerRequestFilter {

  /** Autoridad de los agentes de borde. */
  public static final String DEVICE_ROLE = "ROLE_EDGE_DEVICE";

  private static final Pattern CAMERA_PATH = Pattern.compile("^/api/v1/edge/cameras/([^/]+)/.*$");
  private static final String BEARER = "Bearer ";

  private final DeviceRegistry registry;
  private final ObjectMapper mapper;

  public DeviceAuthenticationFilter(DeviceRegistry registry, ObjectMapper mapper) {
    this.registry = registry;
    this.mapper = mapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<String> cameraId = bearerToken(request).flatMap(this::resolveCamera);
    if (cameraId.isEmpty()) {
      ProblemResponses.write(
          mapper,
          request,
          response,
          HttpStatus.UNAUTHORIZED,
          "UNAUTHORIZED",
          "Token de dispositivo inválido");
      return;
    }
    if (!cameraId.get().equals(pathCameraId(request))) {
      ProblemResponses.write(
          mapper,
          request,
          response,
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "El token no corresponde a la cámara");
      return;
    }
    var authentication =
        new UsernamePasswordAuthenticationToken(
            cameraId.get(), null, List.of(new SimpleGrantedAuthority(DEVICE_ROLE)));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    try {
      chain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private Optional<String> resolveCamera(String token) {
    if (!token.startsWith(EdgeApi.DEVICE_TOKEN_PREFIX)) {
      return Optional.empty();
    }
    return registry.cameraIdForTokenHash(DeviceTokens.hash(token));
  }

  private static Optional<String> bearerToken(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith(BEARER)) {
      return Optional.empty();
    }
    return Optional.of(header.substring(BEARER.length()).trim());
  }

  private static String pathCameraId(HttpServletRequest request) {
    Matcher matcher = CAMERA_PATH.matcher(request.getRequestURI());
    return matcher.matches() ? matcher.group(1) : "";
  }
}

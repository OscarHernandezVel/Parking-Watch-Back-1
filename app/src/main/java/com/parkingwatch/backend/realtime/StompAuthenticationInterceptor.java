package com.parkingwatch.backend.realtime;

import com.parkingwatch.backend.security.DeviceRegistry;
import com.parkingwatch.backend.security.DeviceTokens;
import com.parkingwatch.common.contract.EdgeApi;
import com.parkingwatch.common.contract.EdgeStomp;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Seguridad del canal STOMP (RF-8.1, RNF-4.3). En el CONNECT, el navegador presenta su JWT y el
 * Edge su token de dispositivo en el encabezado Authorization; sin credencial válida la sesión se
 * cierra. Luego se autoriza cada SUBSCRIBE y SEND: el Edge solo envía a /app/edge y solo se
 * suscribe a sus colas de usuario; el navegador solo se suscribe a /topic (las alertas, solo
 * administradores) y no envía mensajes.
 */
public class StompAuthenticationInterceptor implements ChannelInterceptor {

  private static final String BEARER = "Bearer ";
  private static final String EDGE_SEND_PREFIX = "/app/edge/";
  private static final String ALERTS_TOPIC = "/topic/alerts";
  private static final String ADMINISTRATOR = "ROLE_ADMINISTRATOR";

  private final JwtDecoder jwtDecoder;
  private final JwtAuthenticationConverter jwtConverter;
  private final DeviceRegistry devices;
  private final Clock clock;

  public StompAuthenticationInterceptor(
      JwtDecoder jwtDecoder,
      JwtAuthenticationConverter jwtConverter,
      DeviceRegistry devices,
      Clock clock) {
    this.jwtDecoder = jwtDecoder;
    this.jwtConverter = jwtConverter;
    this.devices = devices;
    this.clock = clock;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() == null) {
      return message;
    }
    StompCommand command = accessor.getCommand();
    if (command == StompCommand.CONNECT) {
      accessor.setUser(authenticate(accessor));
    } else if (command == StompCommand.SUBSCRIBE) {
      authorizeSubscribe(accessor.getUser(), accessor.getDestination());
    } else if (command == StompCommand.SEND) {
      authorizeSend(accessor.getUser(), accessor.getDestination());
    }
    return message;
  }

  private Principal authenticate(StompHeaderAccessor accessor) {
    String token =
        bearer(accessor.getFirstNativeHeader(EdgeStomp.AUTHORIZATION_HEADER))
            .orElseThrow(() -> denied("Falta la credencial en el CONNECT"));
    if (kindOf(accessor) == EndpointKind.EDGE) {
      return authenticateDevice(token);
    }
    try {
      return jwtConverter.convert(jwtDecoder.decode(token));
    } catch (JwtException e) {
      throw new MessagingException("Sesión inválida o vencida", e);
    }
  }

  private DevicePrincipal authenticateDevice(String token) {
    if (!token.startsWith(EdgeApi.DEVICE_TOKEN_PREFIX)) {
      throw denied("Token de dispositivo inválido");
    }
    return devices
        .cameraIdForTokenHash(DeviceTokens.hash(token))
        .map(DevicePrincipal::new)
        .orElseThrow(() -> denied("Token de dispositivo inválido"));
  }

  private void authorizeSubscribe(Principal user, String destination) {
    if (user instanceof DevicePrincipal) {
      authorizeDeviceSubscribe(destination);
      return;
    }
    JwtAuthenticationToken session = requireBrowser(user);
    if (destination == null || !destination.startsWith(BrowserTopics.TOPIC_PREFIX + "/")) {
      throw denied("Destino no permitido");
    }
    if (destination.startsWith(ALERTS_TOPIC) && !isAdministrator(session)) {
      throw denied("Las alertas son solo para administradores");
    }
  }

  private static void authorizeDeviceSubscribe(String destination) {
    if (!EdgeStomp.CONFIG_QUEUE.equals(destination)
        && !EdgeStomp.EVENT_RESULTS_QUEUE.equals(destination)) {
      throw denied("El dispositivo no puede suscribirse a " + destination);
    }
  }

  private void authorizeSend(Principal user, String destination) {
    if (!(user instanceof DevicePrincipal)
        || destination == null
        || !destination.startsWith(EDGE_SEND_PREFIX)) {
      throw denied("Envío no permitido a " + destination);
    }
  }

  private JwtAuthenticationToken requireBrowser(Principal user) {
    if (!(user instanceof JwtAuthenticationToken session)) {
      throw denied("Sesión no autenticada");
    }
    Instant expiresAt = session.getToken().getExpiresAt();
    if (expiresAt != null && expiresAt.isBefore(clock.instant())) {
      throw denied("La sesión venció; vuelva a conectarse");
    }
    return session;
  }

  private static boolean isAdministrator(JwtAuthenticationToken session) {
    return session.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(ADMINISTRATOR::equals);
  }

  private static EndpointKind kindOf(StompHeaderAccessor accessor) {
    Map<String, Object> attributes = accessor.getSessionAttributes();
    Object kind = attributes == null ? null : attributes.get(EndpointKind.ATTRIBUTE);
    return kind instanceof EndpointKind endpoint ? endpoint : EndpointKind.BROWSER;
  }

  private static Optional<String> bearer(String header) {
    if (header == null || !header.startsWith(BEARER)) {
      return Optional.empty();
    }
    return Optional.of(header.substring(BEARER.length()).trim());
  }

  private static MessageDeliveryException denied(String reason) {
    return new MessageDeliveryException(reason);
  }
}

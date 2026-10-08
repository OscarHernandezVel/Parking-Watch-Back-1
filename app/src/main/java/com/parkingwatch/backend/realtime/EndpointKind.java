package com.parkingwatch.backend.realtime;

import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Tipo de cliente según el endpoint WebSocket por el que se conectó: navegador ({@code /ws}) o Edge
 * ({@code /ws/edge}). Se guarda en los atributos de la sesión para autenticar el CONNECT.
 */
public enum EndpointKind {
  BROWSER,
  EDGE;

  /** Atributo de sesión con el tipo de cliente. */
  public static final String ATTRIBUTE = "cupo.endpoint";

  /** Interceptor del handshake que marca la sesión con este tipo de cliente. */
  public HandshakeInterceptor marker() {
    return new Marker(this);
  }

  /** Guarda el tipo de cliente en los atributos de la sesión WebSocket. */
  private record Marker(EndpointKind kind) implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler handler,
        Map<String, Object> attributes) {
      attributes.put(ATTRIBUTE, kind);
      return true;
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler handler,
        Exception exception) {
      // Sin acciones posteriores al handshake.
    }
  }
}

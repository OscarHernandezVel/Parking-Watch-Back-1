package com.parkingwatch.backend.realtime;

import com.parkingwatch.backend.config.ParkingProperties;
import com.parkingwatch.backend.security.DeviceRegistry;
import com.parkingwatch.common.contract.EdgeStomp;
import java.time.Clock;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Canal en tiempo real (PB-08): WebSocket con STOMP sobre WSS, el único transporte de larga
 * duración que Render publica (no se usan MQTT ni WebRTC).
 *
 * <ul>
 *   <li>{@code /ws}: navegadores autenticados con JWT; reciben video, detecciones, indicadores,
 *       estados de cámaras y reportes en los tópicos {@code /topic/**}.
 *   <li>{@code /ws/edge}: el Edge autenticado con su token de dispositivo; envía a {@code
 *       /app/edge/**} y recibe la configuración en {@code /user/queue/config}.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer, DisposableBean {

  private static final long HEARTBEAT_MS = 10_000;

  private final ParkingProperties properties;
  private final StompAuthenticationInterceptor authentication;
  private final ThreadPoolTaskScheduler heartbeatScheduler;

  public WebSocketConfig(
      ParkingProperties properties,
      JwtDecoder jwtDecoder,
      JwtAuthenticationConverter jwtConverter,
      DeviceRegistry devices,
      Clock clock) {
    this.properties = properties;
    this.authentication =
        new StompAuthenticationInterceptor(jwtDecoder, jwtConverter, devices, clock);
    this.heartbeatScheduler = new ThreadPoolTaskScheduler();
    this.heartbeatScheduler.setPoolSize(1);
    this.heartbeatScheduler.setThreadNamePrefix("stomp-heartbeat-");
    this.heartbeatScheduler.initialize();
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint(BrowserTopics.ENDPOINT)
        .setAllowedOrigins(properties.security().corsOrigins().toArray(String[]::new))
        .addInterceptors(EndpointKind.BROWSER.marker());
    // El Edge no es un navegador: no envía Origin y se autentica con su token en el CONNECT.
    registry.addEndpoint(EdgeStomp.ENDPOINT).addInterceptors(EndpointKind.EDGE.marker());
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry
        .enableSimpleBroker("/topic", "/queue")
        .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
        .setTaskScheduler(heartbeatScheduler);
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authentication);
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    ParkingProperties.Realtime realtime = properties.realtime();
    registration
        .setMessageSizeLimit(realtime.maxMessageBytes())
        .setSendBufferSizeLimit(realtime.sendBufferBytes())
        .setSendTimeLimit((int) realtime.sendTimeLimit().toMillis())
        .addDecoratorFactory(LatestFrameHandler::new);
  }

  @Override
  public void destroy() {
    heartbeatScheduler.shutdown();
  }

  /** Búferes del contenedor WebSocket suficientes para un fotograma JPEG completo. */
  @Bean
  ServletServerContainerFactoryBean webSocketContainer(ParkingProperties properties) {
    ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
    container.setMaxBinaryMessageBufferSize(properties.realtime().maxMessageBytes());
    container.setMaxTextMessageBufferSize(properties.realtime().maxMessageBytes());
    return container;
  }

  /** Envuelve cada sesión en {@link LatestFrameSession} al conectarse. */
  static final class LatestFrameHandler extends WebSocketHandlerDecorator {

    LatestFrameHandler(WebSocketHandler delegate) {
      super(delegate);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
      super.afterConnectionEstablished(new LatestFrameSession(session));
    }
  }
}

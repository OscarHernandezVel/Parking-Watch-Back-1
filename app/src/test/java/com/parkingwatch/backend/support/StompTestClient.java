package com.parkingwatch.backend.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Cliente STOMP para las pruebas del canal en tiempo real: se conecta como el Edge ({@code
 * /ws/edge}) o como un navegador ({@code /ws}) y guarda en colas los mensajes recibidos.
 */
public final class StompTestClient {

  private static final long TIMEOUT_SECONDS = 10;

  private final WebSocketStompClient client;
  private final ObjectMapper mapper;
  private final int port;

  StompTestClient(int port, ObjectMapper mapper) {
    this.port = port;
    this.mapper = mapper;
    this.client = new WebSocketStompClient(new StandardWebSocketClient());
    this.client.setMessageConverter(new SimpleMessageConverter());
    this.client.setInboundMessageSizeLimit(1024 * 1024);
  }

  /** Mensaje recibido con sus encabezados y cuerpo. */
  public record Received(StompHeaders headers, byte[] body) {}

  /** Conecta con el encabezado Authorization indicado; falla si el servidor rechaza la sesión. */
  public StompSession connect(String endpoint, String authorization) throws Exception {
    StompHeaders connect = new StompHeaders();
    connect.add("Authorization", authorization);
    return client
        .connectAsync(
            "ws://localhost:" + port + endpoint,
            new WebSocketHttpHeaders(),
            connect,
            new StompSessionHandlerAdapter() {})
        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }

  /** Se suscribe y retorna la cola con lo que llegue al destino. */
  public BlockingQueue<Received> subscribe(StompSession session, String destination) {
    BlockingQueue<Received> queue = new LinkedBlockingQueue<>();
    session.subscribe(
        destination,
        new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            queue.add(new Received(headers, (byte[]) payload));
          }
        });
    return queue;
  }

  /** Envía un objeto como JSON. */
  public void sendJson(StompSession session, String destination, Object payload) throws Exception {
    StompHeaders headers = new StompHeaders();
    headers.setDestination(destination);
    headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
    session.send(headers, mapper.writeValueAsBytes(payload));
  }

  /** Envía un cuerpo binario (fotograma JPEG). */
  public void sendBinary(StompSession session, String destination, byte[] payload) {
    StompHeaders headers = new StompHeaders();
    headers.setDestination(destination);
    headers.setContentType(MimeTypeUtils.APPLICATION_OCTET_STREAM);
    session.send(headers, payload);
  }

  /** Espera el siguiente mensaje de la cola. */
  public static Received next(BlockingQueue<Received> queue) throws InterruptedException {
    Received received = queue.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (received == null) {
      throw new AssertionError("No llegó ningún mensaje en " + TIMEOUT_SECONDS + " s");
    }
    return received;
  }

  /** Cuerpo JSON de un mensaje. */
  public JsonNode json(Received received) throws Exception {
    return mapper.readTree(received.body());
  }

  /** Espera un mensaje cuyo tipo de evento sea el indicado, ignorando los demás. */
  public JsonNode nextEvent(BlockingQueue<Received> queue, String type) throws Exception {
    while (true) {
      JsonNode event = json(next(queue));
      if (type.equals(event.path("type").asText())) {
        return event;
      }
    }
  }

  /** Espera un poco para que el servidor registre una suscripción. */
  public static void settle() throws InterruptedException {
    TimeUnit.MILLISECONDS.sleep(300);
  }
}

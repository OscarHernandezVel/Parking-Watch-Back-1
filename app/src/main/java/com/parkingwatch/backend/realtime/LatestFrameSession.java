package com.parkingwatch.backend.realtime;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;

/**
 * Sesión de un navegador que nunca acumula video (RF-8.2): si el navegador va retrasado, los
 * fotogramas en espera se reemplazan por el más reciente, de modo que solo recibe la imagen actual
 * y el retraso no crece. Los demás mensajes (detecciones, reportes) se envían todos y en orden.
 */
public class LatestFrameSession extends WebSocketSessionDecorator {

  private static final Logger LOG = LoggerFactory.getLogger(LatestFrameSession.class);
  private static final String NEWLINE = String.valueOf((char) 10);
  private static final int MAX_HEADER_BYTES = 512;
  private static final String MESSAGE_FRAME = "MESSAGE";
  private static final String VIDEO_DESTINATION = "destination:/topic/cameras/";
  private static final String VIDEO_SUFFIX = "/video";

  private final ReentrantLock sendLock = new ReentrantLock();
  private final AtomicReference<WebSocketMessage<?>> pendingFrame = new AtomicReference<>();
  private final AtomicBoolean draining = new AtomicBoolean();

  public LatestFrameSession(WebSocketSession delegate) {
    super(delegate);
  }

  @Override
  public void sendMessage(WebSocketMessage<?> message) throws IOException {
    if (isVideoFrame(message)) {
      if (pendingFrame.getAndSet(message) == null) {
        startDrain();
      }
      return;
    }
    sendLock.lock();
    try {
      super.sendMessage(message);
    } finally {
      sendLock.unlock();
    }
  }

  /** Fotogramas reemplazados aún sin enviar (0 o 1). */
  boolean hasPendingFrame() {
    return pendingFrame.get() != null;
  }

  private void startDrain() {
    if (draining.compareAndSet(false, true)) {
      Thread.ofVirtual().name("video-" + getId()).start(this::drain);
    }
  }

  private void drain() {
    try {
      WebSocketMessage<?> frame;
      while ((frame = pendingFrame.getAndSet(null)) != null) {
        sendLock.lock();
        try {
          if (isOpen()) {
            super.sendMessage(frame);
          }
        } finally {
          sendLock.unlock();
        }
      }
    } catch (IOException | RuntimeException e) {
      LOG.debug("No se pudo enviar un fotograma a la sesión {}: {}", getId(), e.getMessage());
    } finally {
      draining.set(false);
      if (pendingFrame.get() != null) {
        startDrain();
      }
    }
  }

  /** Trama STOMP MESSAGE binaria cuyo destino es el video de una cámara. */
  static boolean isVideoFrame(WebSocketMessage<?> message) {
    if (!(message instanceof BinaryMessage binary)) {
      return false;
    }
    ByteBuffer payload = binary.getPayload().duplicate();
    byte[] head = new byte[Math.min(payload.remaining(), MAX_HEADER_BYTES)];
    payload.get(head);
    String[] lines = new String(head, StandardCharsets.ISO_8859_1).split(NEWLINE, -1);
    return MESSAGE_FRAME.equals(lines[0])
        && Arrays.stream(lines)
            .takeWhile(line -> !line.isEmpty())
            .anyMatch(line -> line.startsWith(VIDEO_DESTINATION) && line.endsWith(VIDEO_SUFFIX));
  }
}

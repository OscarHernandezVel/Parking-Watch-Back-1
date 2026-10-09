package com.parkingwatch.backend.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/** Un navegador retrasado recibe solo el fotograma más reciente (RF-8.2). */
class LatestFrameSessionTest {

  private static final char NL = (char) 10;

  @Test
  void recognizesVideoFramesByTheirStompDestination() {
    assertThat(LatestFrameSession.isVideoFrame(frame("/topic/cameras/CAM-1/video", 1))).isTrue();
    assertThat(LatestFrameSession.isVideoFrame(frame("/topic/cameras/CAM-1/detections", 1)))
        .isFalse();
    assertThat(LatestFrameSession.isVideoFrame(new TextMessage("MESSAGE"))).isFalse();
    assertThat(LatestFrameSession.isVideoFrame(new BinaryMessage(bytes("CONNECTED" + NL + NL))))
        .isFalse();
  }

  @Test
  void slowBrowsersOnlyGetTheLatestFrameAndAllOtherMessages() throws Exception {
    WebSocketSession delegate = mock(WebSocketSession.class);
    when(delegate.isOpen()).thenReturn(true);
    when(delegate.getId()).thenReturn("s1");
    List<WebSocketMessage<?>> sent = new CopyOnWriteArrayList<>();
    CountDownLatch slow = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              WebSocketMessage<?> message = invocation.getArgument(0);
              if (sent.isEmpty()) {
                slow.await(5, TimeUnit.SECONDS);
              }
              sent.add(message);
              return null;
            })
        .when(delegate)
        .sendMessage(any());
    LatestFrameSession session = new LatestFrameSession(delegate);

    session.sendMessage(frame("/topic/cameras/CAM-1/video", 1));
    await().atMost(Duration.ofSeconds(2)).until(() -> !session.hasPendingFrame());
    for (int i = 2; i <= 5; i++) {
      session.sendMessage(frame("/topic/cameras/CAM-1/video", i));
    }
    assertThat(session.hasPendingFrame()).isTrue();
    slow.countDown();
    await().atMost(Duration.ofSeconds(5)).until(() -> sent.size() == 2);
    session.sendMessage(new TextMessage("MESSAGE"));

    await().atMost(Duration.ofSeconds(5)).until(() -> sent.size() == 3);
    assertThat(lastByte(sent.get(0))).isEqualTo((byte) 1);
    assertThat(lastByte(sent.get(1))).isEqualTo((byte) 5);
    assertThat(sent.get(2)).isInstanceOf(TextMessage.class);
  }

  private static BinaryMessage frame(String destination, int marker) {
    byte[] header =
        bytes(
            "MESSAGE"
                + NL
                + "destination:"
                + destination
                + NL
                + "content-type:application/octet-stream"
                + NL
                + NL);
    byte[] payload = new byte[header.length + 1];
    System.arraycopy(header, 0, payload, 0, header.length);
    payload[header.length] = (byte) marker;
    return new BinaryMessage(payload);
  }

  private static byte lastByte(WebSocketMessage<?> message) {
    byte[] payload = ((BinaryMessage) message).getPayload().array();
    return payload[payload.length - 1];
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.US_ASCII);
  }
}

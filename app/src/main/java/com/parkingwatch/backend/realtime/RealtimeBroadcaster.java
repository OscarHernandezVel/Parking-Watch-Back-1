package com.parkingwatch.backend.realtime;

import com.parkingwatch.common.contract.EdgeStomp;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

/**
 * Envía mensajes a los navegadores suscritos (RF-8.2). Los fotogramas de video se envían con tipo
 * binario para que STOMP los transmita como trama binaria sin codificar.
 */
@Component
public class RealtimeBroadcaster {

  private final SimpMessageSendingOperations messaging;

  public RealtimeBroadcaster(SimpMessageSendingOperations messaging) {
    this.messaging = messaging;
  }

  /** Envía un objeto como JSON a un tópico. */
  public void send(String destination, Object payload) {
    messaging.convertAndSend(destination, payload);
  }

  /** Envía un fotograma JPEG al tópico de video de la cámara. */
  public void sendVideoFrame(String cameraId, byte[] jpeg) {
    messaging.send(
        BrowserTopics.video(cameraId),
        MessageBuilder.withPayload(jpeg)
            .setHeader(
                MessageHeaders.CONTENT_TYPE,
                MimeTypeUtils.parseMimeType(EdgeStomp.VIDEO_CONTENT_TYPE))
            .build());
  }

  /** Envía un mensaje a la cola de un usuario (el Edge se identifica con el id de su cámara). */
  public void sendToUser(String user, String queue, Object payload) {
    messaging.convertAndSendToUser(user, EdgeStomp.userDestination(queue), payload);
  }
}

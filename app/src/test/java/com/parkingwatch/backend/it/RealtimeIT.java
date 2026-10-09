package com.parkingwatch.backend.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.parkingwatch.backend.support.EdgeEvents;
import com.parkingwatch.backend.support.EvidenceUploads;
import com.parkingwatch.backend.support.IntegrationTest;
import com.parkingwatch.backend.support.StompTestClient;
import com.parkingwatch.backend.support.StompTestClient.Received;
import com.parkingwatch.common.contract.ContractExamples;
import com.parkingwatch.common.contract.EdgeStomp;
import com.parkingwatch.common.contract.EventBatch;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.StompSession;

/** Canal en tiempo real Edge, Backend y navegador por WebSocket STOMP (PB-08). */
class RealtimeIT extends IntegrationTest {

  private static final String CAMERA_TOPIC = "/topic/cameras/" + CAMERA_ID;

  @Test
  void videoAndDetectionsReachTheBrowser() throws Exception {
    StompTestClient client = stomp();
    StompSession edge = client.connect(EdgeStomp.ENDPOINT, bearer(DEVICE_TOKEN));
    StompSession browser = client.connect("/ws", bearer(adminToken()));
    BlockingQueue<Received> video = client.subscribe(browser, CAMERA_TOPIC + "/video");
    BlockingQueue<Received> detections = client.subscribe(browser, CAMERA_TOPIC + "/detections");
    StompTestClient.settle();

    byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFE, 0, (byte) 0x80, (byte) 0xFF, (byte) 0xD9};
    client.sendBinary(edge, EdgeStomp.VIDEO, jpeg);
    assertThat(StompTestClient.next(video).body()).isEqualTo(jpeg);

    client.sendJson(edge, EdgeStomp.DETECTIONS, json(ContractExamples.read("detection-snapshot")));
    assertThat(client.json(StompTestClient.next(detections)).get("vehicles")).hasSize(2);

    client.sendBinary(edge, EdgeStomp.VIDEO, new byte[] {1, 2, 3});
    assertThat(video.poll(500, TimeUnit.MILLISECONDS)).as("no JPEG se descarta").isNull();
  }

  @Test
  void eventsAreAcknowledgedAndTheReportIsPublished() throws Exception {
    StompTestClient client = stomp();
    StompSession edge = client.connect(EdgeStomp.ENDPOINT, bearer(DEVICE_TOKEN));
    BlockingQueue<Received> results = client.subscribe(edge, EdgeStomp.EVENT_RESULTS_QUEUE);
    StompSession browser = client.connect("/ws", bearer(adminToken()));
    BlockingQueue<Received> reports = client.subscribe(browser, "/topic/reports");
    StompTestClient.settle();

    var event =
        EdgeEvents.event().track(700).evidence(EvidenceUploads.upload(mvc, mapper)).exceeded();
    client.sendJson(edge, EdgeStomp.EVENTS, new EventBatch(List.of(event)));

    JsonNode result = client.json(StompTestClient.next(results)).get("results").get(0);
    assertThat(result.get("eventId").asText()).isEqualTo(event.eventId().toString());
    assertThat(result.get("outcome").asText()).isEqualTo("ACCEPTED");
    JsonNode created = client.nextEvent(reports, "report.created");
    assertThat(created.at("/payload/plate").asText()).isEqualTo("ABC123");
    assertThat(created.at("/payload/id").asText()).isEqualTo(result.get("reportId").asText());
  }

  @Test
  void zoneChangesArePushedToTheEdge() throws Exception {
    StompTestClient client = stomp();
    StompSession edge = client.connect(EdgeStomp.ENDPOINT, bearer(DEVICE_TOKEN));
    BlockingQueue<Received> config = client.subscribe(edge, EdgeStomp.CONFIG_QUEUE);
    StompTestClient.settle();

    String admin = adminToken();
    String zones =
        mvc.perform(
                get("/api/v1/cameras/" + CAMERA_ID + "/zones")
                    .header("Authorization", bearer(admin)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    mvc.perform(
            put("/api/v1/cameras/" + CAMERA_ID + "/zones")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("zones", json(zones)))))
        .andExpect(status().isOk());

    JsonNode pushed = client.json(StompTestClient.next(config));
    assertThat(pushed.get("cameraId").asText()).isEqualTo(CAMERA_ID);
    assertThat(pushed.get("zones")).hasSize(5);
    assertThat(pushed.at("/settings/detectionSampleIntervalMs").asInt()).isEqualTo(200);
    assertThat(pushed.at("/settings/video/width").asInt()).isEqualTo(960);
  }

  @Test
  void heartbeatAndAnalyticsUpdateTheLiveView() throws Exception {
    StompTestClient client = stomp();
    StompSession edge = client.connect(EdgeStomp.ENDPOINT, bearer(DEVICE_TOKEN));
    client.sendJson(edge, EdgeStomp.HEARTBEAT, json(ContractExamples.read("heartbeat")));
    client.sendJson(edge, EdgeStomp.ANALYTICS, json(ContractExamples.read("analytics-snapshot")));
    String admin = adminToken();
    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                mvc.perform(
                        get("/api/v1/cameras/" + CAMERA_ID + "/live")
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.connected").value(true))
                    .andExpect(jsonPath("$.analytics.fps").value(12.1)));
  }

  @Test
  void invalidCredentialsAndForbiddenDestinationsAreRejected() throws Exception {
    StompTestClient client = stomp();
    assertThatThrownBy(() -> client.connect(EdgeStomp.ENDPOINT, "Bearer pwd_falso"))
        .isInstanceOf(Exception.class);
    assertThatThrownBy(() -> client.connect("/ws", "Bearer basura")).isInstanceOf(Exception.class);
    assertThatThrownBy(() -> client.connect(EdgeStomp.ENDPOINT, bearer(adminToken())))
        .isInstanceOf(Exception.class);

    StompSession browser = client.connect("/ws", bearer(adminToken()));
    client.sendJson(browser, EdgeStomp.HEARTBEAT, json(ContractExamples.read("heartbeat")));
    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(10))
        .until(() -> !browser.isConnected());
  }
}

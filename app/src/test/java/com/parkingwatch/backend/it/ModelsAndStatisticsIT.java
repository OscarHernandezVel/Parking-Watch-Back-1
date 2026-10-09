package com.parkingwatch.backend.it;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parkingwatch.backend.support.IntegrationTest;
import com.parkingwatch.backend.support.StompTestClient;
import com.parkingwatch.common.contract.EdgeStomp;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.web.servlet.ResultActions;

/** Versionado del modelo (PB-13), alertas (PB-14) y tendencias (PB-12). */
class ModelsAndStatisticsIT extends IntegrationTest {

  private ResultActions registerModel(String admin, String version, double recall)
      throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("version", version);
    body.put("trainedAt", "2026-09-30T00:00:00Z");
    body.put("map50", 0.93);
    body.put("precision", 0.97);
    body.put("recall", recall);
    body.put("plateAccuracy", 0.91);
    return mvc.perform(
        post("/api/v1/models")
            .header("Authorization", bearer(admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body)));
  }

  @Test
  void modelsAreRegisteredActivatedGatedAndRolledBack() throws Exception {
    String admin = adminToken();
    registerModel(admin, "det-1.1", 0.92).andExpect(status().isCreated());
    registerModel(admin, "det-1.1", 0.92).andExpect(status().isConflict());
    mvc.perform(post("/api/v1/models/det-1.1/activate").header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));
    mvc.perform(post("/api/v1/models/rollback").header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value("det-1.0-maqueta"));
    registerModel(admin, "det-bad", 0.4).andExpect(status().isCreated());
    mvc.perform(post("/api/v1/models/det-bad/activate").header("Authorization", bearer(admin)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("QUALITY_GATE_FAILED"));
    mvc.perform(get("/api/v1/models").header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.version == 'det-bad')].qualityGate.passed").value(false));
  }

  @Test
  void driftAlertsAreListedAndAcknowledgedOnce() throws Exception {
    String admin = adminToken();
    sendFromEdge(
        EdgeStomp.ALERTS,
        Map.of(
            "alertType", "CONFIDENCE_DRIFT",
            "observedValue", 0.5,
            "baselineValue", 0.9,
            "detectedAt", "2026-10-01T14:00:00Z",
            "message", "Confianza baja"));
    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                jdbc.queryForObject(
                        "SELECT count(*) FROM model_alerts WHERE alert_type = 'CONFIDENCE_DRIFT'",
                        Integer.class)
                    > 0);
    String body =
        mvc.perform(get("/api/v1/alerts").header("Authorization", bearer(admin)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = -1;
    for (var alert : json(body)) {
      if ("CONFIDENCE_DRIFT".equals(alert.get("alertType").asText())) {
        id = alert.get("id").asLong();
      }
    }
    mvc.perform(
            post("/api/v1/alerts/" + id + "/acknowledge").header("Authorization", bearer(admin)))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/v1/alerts/" + id + "/acknowledge").header("Authorization", bearer(admin)))
        .andExpect(status().isNotFound());
  }

  @Test
  void statisticsAreAggregatedByHourInColombianTime() throws Exception {
    Map<String, Object> zone1 = new HashMap<>();
    zone1.put("zoneId", 1);
    zone1.put("vehiclesDetected", 3);
    zone1.put("occupancyPct", 50);
    zone1.put("avgDwellSeconds", 20);
    zone1.put("fps", 12);
    zone1.put("avgConfidence", 0.9);
    Map<String, Object> unknown = new HashMap<>(zone1);
    unknown.put("zoneId", 999);
    unknown.put("avgDwellSeconds", null);
    sendFromEdge(
        EdgeStomp.STATISTICS,
        Map.of("minute", "2026-09-30T10:05:00Z", "zones", List.of(zone1, unknown)));
    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                jdbc.queryForObject(
                        "SELECT count(*) FROM minute_statistics WHERE minute = '2026-09-30T10:05:00Z'",
                        Integer.class)
                    > 0);
    String admin = adminToken();
    mvc.perform(
            get("/api/v1/statistics?from=2026-09-30T00:00:00Z&to=2026-10-01T00:00:00Z&zoneId=1&granularity=HOUR")
                .header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].vehiclesDetected").value(3))
        .andExpect(jsonPath("$[0].occupancyPct").value(50.0));
    mvc.perform(
            get("/api/v1/statistics?from=2026-10-02T00:00:00Z&to=2026-10-01T00:00:00Z")
                .header("Authorization", bearer(admin)))
        .andExpect(status().isBadRequest());
  }

  private void sendFromEdge(String destination, Object payload) throws Exception {
    StompTestClient client = stomp();
    StompSession edge = client.connect(EdgeStomp.ENDPOINT, bearer(DEVICE_TOKEN));
    client.sendJson(edge, destination, payload);
  }
}

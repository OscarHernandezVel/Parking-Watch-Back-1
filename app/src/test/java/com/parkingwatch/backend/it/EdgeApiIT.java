package com.parkingwatch.backend.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.parkingwatch.backend.support.Containers;
import com.parkingwatch.backend.support.EdgeEvents;
import com.parkingwatch.backend.support.EvidenceUploads;
import com.parkingwatch.backend.support.IntegrationTest;
import com.parkingwatch.common.contract.EdgeApi;
import com.parkingwatch.common.contract.EventBatch;
import com.parkingwatch.common.contract.EvidenceKeys;
import com.parkingwatch.common.contract.ParkingEventMessage;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** Acoplamiento del Edge con el backend por HTTPS (RF-8.3, RF-15.3). */
class EdgeApiIT extends IntegrationTest {

  private ResultActions postEdge(String resource, Object body) throws Exception {
    return mvc.perform(
        post(EdgeApi.pathFor(CAMERA_ID, resource))
            .header("Authorization", bearer(DEVICE_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body)));
  }

  private JsonNode sendEvents(ParkingEventMessage... events) throws Exception {
    String body =
        postEdge(EdgeApi.EVENTS_BATCH, new EventBatch(List.of(events)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json(body).get("results");
  }

  @Test
  void configurationIsServedWithEtagAndNotModified() throws Exception {
    String etag =
        mvc.perform(
                get(EdgeApi.pathFor(CAMERA_ID, EdgeApi.CONFIGURATION))
                    .header("Authorization", bearer(DEVICE_TOKEN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zones.length()").value(5))
            .andExpect(jsonPath("$.model.version").value("det-1.0-maqueta"))
            .andReturn()
            .getResponse()
            .getHeader("ETag");
    mvc.perform(
            get(EdgeApi.pathFor(CAMERA_ID, EdgeApi.CONFIGURATION))
                .header("Authorization", bearer(DEVICE_TOKEN))
                .header("If-None-Match", etag))
        .andExpect(status().isNotModified());
  }

  @Test
  void devicesCanOnlyAccessTheirOwnCamera() throws Exception {
    String path = EdgeApi.pathFor(CAMERA_ID, EdgeApi.CONFIGURATION);
    mvc.perform(get(path))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    mvc.perform(get(path).header("Authorization", "Bearer pwd_falso"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get(EdgeApi.pathFor("CAM-OTRA", EdgeApi.CONFIGURATION))
                .header("Authorization", bearer(DEVICE_TOKEN)))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidMessagesAreRejectedWithTheContractIssues() throws Exception {
    postEdge(EdgeApi.EVENTS_BATCH, Map.of("events", List.of(Map.of("zoneId", -1))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details.issues").isArray());
  }

  @Test
  void aStayProducesOneReportIdempotentlyAndClosesOnExit() throws Exception {
    EvidenceKeys evidence = EvidenceUploads.upload(mvc, mapper);
    EdgeEvents stay = EdgeEvents.event().track(100).evidence(evidence);
    ParkingEventMessage exceeded = stay.exceeded();
    JsonNode results = sendEvents(stay.entered(), exceeded, stay.exited(40));
    assertThat(results.findValuesAsText("outcome"))
        .containsExactly("ACCEPTED", "ACCEPTED", "ACCEPTED");
    String reportId = results.get(1).get("reportId").asText();

    JsonNode replay =
        sendEvents(exceeded, EdgeEvents.event().track(100).evidence(evidence).exceeded());
    assertThat(replay.findValuesAsText("outcome")).containsExactly("DUPLICATE", "DUPLICATE");
    Integer total =
        jdbc.queryForObject(
            "SELECT total_seconds FROM reports WHERE id = ?::uuid", Integer.class, reportId);
    assertThat(total).isEqualTo(40);
  }

  @Test
  void theRulesEngineRejectsUnsignaledZonesLowConfidenceAndForeignEvidence() throws Exception {
    EvidenceKeys foreign =
        new EvidenceKeys(
            "evidence/CAM-OTRA/a.jpg", "evidence/CAM-OTRA/b.jpg", "evidence/CAM-OTRA/c.jpg");
    JsonNode results =
        sendEvents(
            EdgeEvents.event().zone(5).track(200).exceeded(),
            EdgeEvents.event().track(201).confidence(0.2).exceeded(),
            EdgeEvents.event().track(202).evidence(foreign).exceeded(),
            EdgeEvents.event().track(203).dwell(3).exceeded());
    assertThat(results.findValuesAsText("outcome")).containsOnly("REJECTED");
    assertThat(results.findValuesAsText("reason"))
        .anyMatch(reason -> reason.contains("señalización"));
  }

  @Test
  void evidenceMustBeThreeJpegPhotos() throws Exception {
    mvc.perform(
            multipart(EdgeApi.pathFor(CAMERA_ID, EdgeApi.EVIDENCE))
                .file(EvidenceUploads.photo("entry"))
                .file(EvidenceUploads.photo("report"))
                .file(new MockMultipartFile("plate", "plate.jpg", "image/jpeg", new byte[] {1, 2}))
                .header("Authorization", bearer(DEVICE_TOKEN)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    EvidenceKeys keys = EvidenceUploads.upload(mvc, mapper);
    assertThat(keys.entryPhotoKey()).startsWith("evidence/CAM-MAQ-01/").endsWith("-entry.jpg");
    assertThat(Files.readAllBytes(Containers.stored(keys.platePhotoKey())))
        .isEqualTo(EvidenceUploads.JPEG);
  }

  @Test
  void theActiveModelIsDownloadedAndMatchesItsFingerprint() throws Exception {
    byte[] onnx = {8, 7, 6, 5, 4, 3, 2, 1};
    mvc.perform(
            multipart("/api/v1/models/det-1.0-maqueta/artifacts/detector")
                .file(
                    new MockMultipartFile(
                        "file", "detector.onnx", "application/octet-stream", onnx))
                .header("Authorization", bearer(adminToken())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.detectorSha256").value(sha256(onnx)));
    String path =
        json(mvc.perform(
                    get(EdgeApi.pathFor(CAMERA_ID, EdgeApi.ACTIVE_MODEL))
                        .header("Authorization", bearer(DEVICE_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detector.sha256").value(sha256(onnx)))
                .andReturn()
                .getResponse()
                .getContentAsString())
            .at("/detector/url")
            .asText();
    assertThat(path).isEqualTo(EdgeApi.modelArtifactPath(CAMERA_ID, "det-1.0-maqueta", "detector"));
    byte[] downloaded =
        mvc.perform(get(path).header("Authorization", bearer(DEVICE_TOKEN)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(downloaded).isEqualTo(onnx);
    mvc.perform(
            get(EdgeApi.modelArtifactPath(CAMERA_ID, "det-1.0-maqueta", "plate-reader"))
                .header("Authorization", bearer(DEVICE_TOKEN)))
        .andExpect(status().isNotFound());
  }

  private static String sha256(byte[] content) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
  }
}

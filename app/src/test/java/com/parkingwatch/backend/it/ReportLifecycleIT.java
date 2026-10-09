package com.parkingwatch.backend.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.parkingwatch.backend.retention.RetentionService;
import com.parkingwatch.backend.support.Containers;
import com.parkingwatch.backend.support.EdgeEvents;
import com.parkingwatch.backend.support.EvidenceUploads;
import com.parkingwatch.backend.support.IntegrationTest;
import com.parkingwatch.common.contract.EdgeApi;
import com.parkingwatch.common.contract.EventBatch;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Ciclo de vida de un reporte: detalle, revisión, aprendizaje continuo, exportación y retención.
 */
class ReportLifecycleIT extends IntegrationTest {

  @Autowired RetentionService retention;

  private String createReport(long trackId) throws Exception {
    var event =
        EdgeEvents.event().track(trackId).evidence(EvidenceUploads.upload(mvc, mapper)).exceeded();
    String body =
        mvc.perform(
                post(EdgeApi.pathFor(CAMERA_ID, EdgeApi.EVENTS_BATCH))
                    .header("Authorization", bearer(DEVICE_TOKEN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new EventBatch(List.of(event)))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json(body).get("results").get(0).get("reportId").asText();
  }

  private String review(String id, String token, Map<String, Object> body, int expectedStatus)
      throws Exception {
    return mvc.perform(
            patch("/api/v1/reports/" + id)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().is(expectedStatus))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void detailIncludesEvidenceServedOnlyWithASession() throws Exception {
    String id = createReport(300);
    String admin = adminToken();
    JsonNode detail =
        json(
            mvc.perform(get("/api/v1/reports/" + id).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(detail.at("/zone/name").asText()).isEqualTo("Zona amarilla");
    assertThat(detail.at("/report/plate").asText()).isEqualTo("ABC123");
    String photo = detail.at("/evidenceUrls/plate").asText();
    assertThat(photo).isEqualTo("/api/v1/reports/" + id + "/evidence/plate");
    byte[] body =
        mvc.perform(get(photo).header("Authorization", bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_JPEG))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(body).isEqualTo(EvidenceUploads.JPEG);
    mvc.perform(get(photo)).andExpect(status().isUnauthorized());
  }

  @Test
  void dismissalRequiresAReasonFeedsTheDatasetAndIsFinal() throws Exception {
    String admin = adminToken();
    String id = createReport(301);
    review(id, admin, Map.of("status", "DISMISSED"), 400);
    review(
        id,
        admin,
        Map.of(
            "status", "DISMISSED", "dismissalReason", "PLATE_MISREAD", "correctedPlate", "abc-128"),
        200);
    review(id, admin, Map.of("status", "CONFIRMED"), 409);
    String label = Files.readString(Containers.stored("retraining/plate_misread/" + id + ".json"));
    assertThat(json(label).get("corrected").asText()).isEqualTo("ABC128");
    assertThat(Files.readAllBytes(Containers.stored("retraining/plate_misread/" + id + ".jpg")))
        .isEqualTo(EvidenceUploads.JPEG);
    mvc.perform(get("/api/v1/retraining/status").header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pendingSamples").isNumber());
  }

  @Test
  void confirmedReportsAreListedAndExported() throws Exception {
    String admin = adminToken();
    String id = createReport(302);
    review(id, admin, Map.of("status", "CONFIRMED"), 200);
    mvc.perform(
            get("/api/v1/reports?status=CONFIRMED&size=5").header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));
    byte[] pdf =
        mvc.perform(get("/api/v1/reports/export?format=PDF").header("Authorization", bearer(admin)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
        .isEqualTo("%PDF-");
    byte[] xlsx =
        mvc.perform(
                get("/api/v1/reports/export?format=XLSX&status=CONFIRMED")
                    .header("Authorization", bearer(admin)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      assertThat(workbook.getSheet("Reportes").getRow(0).getCell(4).getStringCellValue())
          .isEqualTo("Placa");
      assertThat(workbook.getSheet("Reportes").getLastRowNum()).isGreaterThanOrEqualTo(1);
    }
  }

  @Test
  void dismissedReportsAndTheirPhotosArePurgedAfterThirtyDays() throws Exception {
    String admin = adminToken();
    String id = createReport(303);
    review(id, admin, Map.of("status", "DISMISSED", "dismissalReason", "MOMENTARY_STOP"), 200);
    CLOCK.advance(Duration.ofDays(31));
    assertThat(retention.purgeDismissedReports()).isGreaterThanOrEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM reports WHERE id = ?::uuid", Long.class, id))
        .isZero();
  }
}

package com.parkingwatch.backend.it;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.parkingwatch.backend.support.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Acceso privado con roles (PB-05) y configuración de zonas (PB-04). */
class AccessAndZonesIT extends IntegrationTest {

  private String operatorToken() throws Exception {
    String admin = adminToken();
    mvc.perform(
        post("/api/v1/users")
            .header("Authorization", bearer(admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                mapper.writeValueAsString(
                    Map.of(
                        "username", "operador1",
                        "fullName", "Operador Uno",
                        "password", "Operador-2026!",
                        "role", "OPERATOR"))));
    return login("operador1", "Operador-2026!");
  }

  @Test
  void loginFailsWithoutRevealingWhichCredentialWasWrong() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"incorrecta\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"));
  }

  @Test
  void operatorsConsultButOnlyAdministratorsConfigure() throws Exception {
    String operator = operatorToken();
    mvc.perform(get("/api/v1/cameras").header("Authorization", bearer(operator)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(CAMERA_ID));
    mvc.perform(get("/api/v1/users").header("Authorization", bearer(operator)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/models").header("Authorization", bearer(operator)))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/v1/cameras/" + CAMERA_ID + "/zones")
                .header("Authorization", bearer(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"zones\":[]}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/cameras")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/cameras").header("Authorization", "Bearer basura"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void expiredSessionsAreRejected() throws Exception {
    String token = adminToken();
    CLOCK.advance(java.time.Duration.ofHours(9));
    mvc.perform(get("/api/v1/cameras").header("Authorization", bearer(token)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void cameraShowsLocationAndCoverageForTheMap() throws Exception {
    mvc.perform(get("/api/v1/cameras/" + CAMERA_ID).header("Authorization", bearer(adminToken())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.location.latitude").value(4.60971))
        .andExpect(jsonPath("$.coverageArea.length()").value(5))
        .andExpect(jsonPath("$.frame.width").value(1280));
    mvc.perform(get("/api/v1/cameras/NO-EXISTE").header("Authorization", bearer(adminToken())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void zonesAreSavedAndInvalidPolygonsRejected() throws Exception {
    String admin = adminToken();
    JsonNode zones =
        json(
            mvc.perform(
                    get("/api/v1/cameras/" + CAMERA_ID + "/zones")
                        .header("Authorization", bearer(admin)))
                .andReturn()
                .getResponse()
                .getContentAsString());
    List<JsonNode> all = new java.util.ArrayList<>();
    zones.forEach(all::add);
    ((ObjectNode) all.get(4)).put("toleranceSeconds", 15);
    mvc.perform(
            put("/api/v1/cameras/" + CAMERA_ID + "/zones")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("zones", all))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[4].toleranceSeconds").value(15));
    Map<String, Object> outside =
        Map.of(
            "name",
            "Fuera",
            "zoneType",
            "CORNER",
            "polygon",
            List.of(Map.of("x", 0, "y", 0), Map.of("x", 5000, "y", 0), Map.of("x", 0, "y", 100)),
            "toleranceSeconds",
            10,
            "signaled",
            true);
    mvc.perform(
            put("/api/v1/cameras/" + CAMERA_ID + "/zones")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("zones", List.of(outside)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void corsAllowsOnlyTheConfiguredWebOrigin() throws Exception {
    mvc.perform(
            options("/api/v1/cameras")
                .header("Origin", "https://web.test")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(header().string("Access-Control-Allow-Origin", "https://web.test"));
    mvc.perform(
            options("/api/v1/cameras")
                .header("Origin", "https://malicioso.test")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }

  /**
   * La salud y el contrato OpenAPI son públicos. El contrato se guarda en target/openapi.json: la
   * integración continua lo publica y el Frontend genera sus tipos con openapi-typescript.
   */
  @Test
  void healthAndOpenApiArePublic() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    String openApi =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/edge/cameras/{cameraId}/events/batch']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/auth/mfa/setup']").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Files.writeString(
        Path.of("target", "openapi.json"),
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json(openApi)));
  }

  @Test
  void administratorsNeedTheirSecondFactor() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        Map.of("username", "admin", "password", ADMIN_PASSWORD))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));
    createUser("admin2", "Admin-Segundo-2026", "ADMINISTRATOR");
    String pending = login("admin2", "Admin-Segundo-2026");
    mvc.perform(get("/api/v1/models").header("Authorization", bearer(pending)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/cameras").header("Authorization", bearer(pending)))
        .andExpect(status().isOk());
    String secret =
        json(mvc.perform(post("/api/v1/auth/mfa/setup").header("Authorization", bearer(pending)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpauthUri").value(startsWith("otpauth://totp/")))
                .andReturn()
                .getResponse()
                .getContentAsString())
            .get("secret")
            .asText();
    String activated =
        json(mvc.perform(
                    post("/api/v1/auth/mfa/activate")
                        .header("Authorization", bearer(pending))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            mapper.writeValueAsString(Map.of("code", totp.currentCode(secret)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaSetupRequired").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString())
            .get("accessToken")
            .asText();
    mvc.perform(get("/api/v1/models").header("Authorization", bearer(activated)))
        .andExpect(status().isOk());
  }

  @Test
  void repeatedFailuresLockTheAccountTemporarily() throws Exception {
    createUser("bloqueo1", "Operador-Bloqueo-2026", "OPERATOR");
    String wrong = mapper.writeValueAsString(Map.of("username", "bloqueo1", "password", "mala"));
    for (int attempt = 0; attempt < 5; attempt++) {
      mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
          .andExpect(status().isUnauthorized());
    }
    String right =
        mapper.writeValueAsString(
            Map.of("username", "bloqueo1", "password", "Operador-Bloqueo-2026"));
    mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(right))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    CLOCK.advance(java.time.Duration.ofMinutes(16));
    mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(right))
        .andExpect(status().isOk());
  }

  @Test
  void sessionsAreShortAndCanBeRenewed() throws Exception {
    String token = adminToken();
    CLOCK.advance(java.time.Duration.ofMinutes(20));
    String renewed =
        json(mvc.perform(post("/api/v1/auth/refresh").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
            .get("accessToken")
            .asText();
    CLOCK.advance(java.time.Duration.ofMinutes(20));
    mvc.perform(get("/api/v1/models").header("Authorization", bearer(token)))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/models").header("Authorization", bearer(renewed)))
        .andExpect(status().isOk());
  }

  private void createUser(String username, String password, String role) throws Exception {
    mvc.perform(
        post("/api/v1/users")
            .header("Authorization", bearer(adminToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                mapper.writeValueAsString(
                    Map.of(
                        "username", username,
                        "fullName", "Usuario " + username,
                        "password", password,
                        "role", role))));
  }
}

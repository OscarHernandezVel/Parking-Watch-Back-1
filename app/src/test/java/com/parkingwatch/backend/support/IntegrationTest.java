package com.parkingwatch.backend.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkingwatch.backend.security.TotpService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Base de las pruebas de integración: Spring Boot completo en un puerto aleatorio (API REST y
 * WebSocket) con PostgreSQL/PostGIS real (Testcontainers), un disco temporal como {@code /data},
 * migraciones de Flyway y datos de la maqueta. Todas las clases comparten un único contexto.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "parking.bootstrap.enabled=true",
      "parking.bootstrap.admin-username=admin",
      "parking.bootstrap.admin-password=" + IntegrationTest.ADMIN_PASSWORD,
      "parking.bootstrap.admin-totp-secret=" + IntegrationTest.ADMIN_TOTP_SECRET,
      "parking.devices.tokens=CAM-MAQ-01=" + IntegrationTest.DEVICE_TOKEN,
      "parking.security.admin-mfa-required=true",
      "parking.security.jwt-secret=integration-secret-with-more-than-32-characters",
      "parking.security.cors-origins=https://web.test",
      "parking.learning.retraining-sample-threshold=2",
      "parking.retention.cron=-",
      "parking.learning.dismissal-check-cron=-"
    })
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(IntegrationTest.ClockConfiguration.class)
public abstract class IntegrationTest {

  public static final String ADMIN_PASSWORD = "Maqueta-Demo-2026";
  public static final String ADMIN_TOTP_SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
  public static final String DEVICE_TOKEN = "pwd_integrationDeviceTokenForTheMaqueta0001";
  public static final String CAMERA_ID = "CAM-MAQ-01";
  public static final Instant NOW = Instant.parse("2026-10-01T15:00:00Z");
  public static final MutableClock CLOCK = new MutableClock(NOW);

  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper mapper;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected TotpService totp;
  @LocalServerPort protected int port;

  @DynamicPropertySource
  static void containers(DynamicPropertyRegistry registry) {
    Containers.register(registry);
  }

  /** Reloj compartido por el contexto de pruebas. */
  @TestConfiguration
  static class ClockConfiguration {
    @Bean
    @Primary
    Clock testClock() {
      return CLOCK;
    }
  }

  @BeforeEach
  void resetClock() {
    CLOCK.set(NOW);
  }

  /** Inicia sesión y retorna el token. */
  protected String login(String username, String password) throws Exception {
    return login(Map.of("username", username, "password", password));
  }

  /** Inicia sesión con el cuerpo indicado (usuario, contraseña y código opcional). */
  protected String login(Map<String, String> credentials) throws Exception {
    String body =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(credentials)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json(body).get("accessToken").asText();
  }

  /** Sesión del administrador con segundo factor (RNF-4.1). */
  protected String adminToken() throws Exception {
    return login(
        Map.of(
            "username",
            "admin",
            "password",
            ADMIN_PASSWORD,
            "otp",
            totp.currentCode(ADMIN_TOTP_SECRET)));
  }

  /** Cliente STOMP conectado al servidor de pruebas. */
  protected StompTestClient stomp() {
    return new StompTestClient(port, mapper);
  }

  protected JsonNode json(String body) throws Exception {
    return mapper.readTree(body);
  }

  protected static String bearer(String token) {
    return "Bearer " + token;
  }
}

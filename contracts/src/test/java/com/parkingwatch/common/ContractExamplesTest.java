package com.parkingwatch.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkingwatch.common.contract.AnalyticsSnapshot;
import com.parkingwatch.common.contract.ContractExamples;
import com.parkingwatch.common.contract.ContractJson;
import com.parkingwatch.common.contract.DetectionSnapshot;
import com.parkingwatch.common.contract.DriftAlertMessage;
import com.parkingwatch.common.contract.EdgeConfiguration;
import com.parkingwatch.common.contract.EventBatch;
import com.parkingwatch.common.contract.EventBatchResult;
import com.parkingwatch.common.contract.EvidenceKeys;
import com.parkingwatch.common.contract.HeartbeatMessage;
import com.parkingwatch.common.contract.MinuteStatisticsMessage;
import com.parkingwatch.common.contract.ParkingEventMessage;
import com.parkingwatch.common.domain.ParkingEventType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pruebas de contrato: los ejemplos publicados en el paquete (classpath {@code contracts/examples})
 * deben deserializarse, validar y volver a serializarse sin perder información. El repositorio
 * cupo-edge usa los mismos ejemplos desde el jar de cupo-contracts.
 */
class ContractExamplesTest {

  private final ObjectMapper mapper = ContractJson.newMapper();
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  static Stream<Arguments> examples() {
    return Stream.of(
        Arguments.of("heartbeat", HeartbeatMessage.class),
        Arguments.of("event-batch", EventBatch.class),
        Arguments.of("event-batch-result", EventBatchResult.class),
        Arguments.of("evidence-keys", EvidenceKeys.class),
        Arguments.of("minute-statistics", MinuteStatisticsMessage.class),
        Arguments.of("detection-snapshot", DetectionSnapshot.class),
        Arguments.of("analytics-snapshot", AnalyticsSnapshot.class),
        Arguments.of("drift-alert", DriftAlertMessage.class),
        Arguments.of("edge-configuration", EdgeConfiguration.class));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("examples")
  void exampleRoundTripsAndValidates(String name, Class<?> type) throws IOException {
    JsonNode original = mapper.readTree(read(name));
    Object message = mapper.treeToValue(original, type);

    assertThat(validator.validate(message)).as("violaciones de %s", name).isEmpty();
    JsonNode serialized = mapper.valueToTree(message);
    assertThat(mapper.treeToValue(serialized, type)).isEqualTo(message);
  }

  @Test
  void toleranceEventWithoutEvidenceIsInvalid() throws IOException {
    EventBatch batch = mapper.readValue(read("event-batch"), EventBatch.class);
    ParkingEventMessage exceeded = batch.events().get(1);
    assertThat(exceeded.eventType()).isEqualTo(ParkingEventType.TOLERANCE_EXCEEDED);

    ParkingEventMessage incomplete =
        new ParkingEventMessage(
            exceeded.eventId(),
            exceeded.eventType(),
            exceeded.zoneId(),
            exceeded.trackId(),
            exceeded.vehicleType(),
            exceeded.detectionConfidence(),
            exceeded.modelVersion(),
            exceeded.enteredAt(),
            exceeded.occurredAt(),
            exceeded.dwellSeconds(),
            exceeded.plate(),
            null);
    assertThat(validator.validate(incomplete)).hasSize(1);
  }

  @Test
  void instantsWithOffsetAreNormalizedToUtc() throws IOException {
    HeartbeatMessage heartbeat = mapper.readValue(read("heartbeat"), HeartbeatMessage.class);
    assertThat(heartbeat.sentAt()).hasToString("2026-10-01T15:12:00Z");
  }

  private static String read(String name) throws IOException {
    return ContractExamples.read(name);
  }
}

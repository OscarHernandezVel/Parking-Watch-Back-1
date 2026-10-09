package com.parkingwatch.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.parkingwatch.common.contract.EdgeApi;
import com.parkingwatch.common.contract.EdgeConfiguration;
import com.parkingwatch.common.contract.EdgeStomp;
import com.parkingwatch.common.contract.EventResult;
import com.parkingwatch.common.contract.PlateReading;
import com.parkingwatch.common.domain.AlertType;
import com.parkingwatch.common.domain.DismissalReason;
import com.parkingwatch.common.domain.PlateNumber;
import com.parkingwatch.common.domain.PlateStatus;
import com.parkingwatch.common.domain.ReportStatus;
import com.parkingwatch.common.domain.VehicleType;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DomainCatalogTest {

  @ParameterizedTest
  @CsvSource({"ABC123,ABC123", "abc-123,ABC123", "' xyz 12a ',XYZ12A"})
  void normalizesValidColombianPlates(String raw, String expected) {
    assertThat(PlateNumber.normalize(raw)).contains(expected);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"AB1234", "ABCD12", "123ABC", "ABC1234"})
  void rejectsInvalidPlates(String raw) {
    assertThat(PlateNumber.normalize(raw)).isEmpty();
  }

  @Test
  void plateFormatMustMatchTheVehicleType() {
    assertThat(PlateNumber.isConsistentWith("ABC123", VehicleType.CAR)).isTrue();
    assertThat(PlateNumber.isConsistentWith("ABC123", VehicleType.MOTORCYCLE)).isFalse();
    assertThat(PlateNumber.isConsistentWith("ABC12D", VehicleType.MOTORCYCLE)).isTrue();
    assertThat(PlateNumber.isConsistentWith("ABC12D", VehicleType.TRUCK)).isFalse();
  }

  @Test
  void catalogsExposeTheirBusinessMeaning() {
    assertThat(Arrays.stream(DismissalReason.values()).filter(DismissalReason::feedsRetraining))
        .containsExactly(DismissalReason.FALSE_POSITIVE, DismissalReason.PLATE_MISREAD);
    assertThat(Arrays.stream(AlertType.values()).filter(AlertType::isDrift))
        .containsExactly(
            AlertType.CONFIDENCE_DRIFT, AlertType.BRIGHTNESS_DRIFT, AlertType.DISMISSAL_RATE_DRIFT);
    assertThat(ReportStatus.valueOf("NEW")).isEqualTo(ReportStatus.NEW);
  }

  @Test
  void contractHelpersBuildConsistentMessages() {
    UUID id = UUID.randomUUID();
    assertThat(EventResult.accepted(id, null).outcome()).isEqualTo(EventResult.Outcome.ACCEPTED);
    assertThat(EventResult.duplicate(id, "x").reason()).isEqualTo("x");
    assertThat(EventResult.rejected(id, "y").outcome()).isEqualTo(EventResult.Outcome.REJECTED);
    assertThat(PlateReading.notRead().status()).isEqualTo(PlateStatus.NOT_READ);
    assertThat(EdgeApi.pathFor("CAM-1", EdgeApi.EVENTS_BATCH))
        .isEqualTo("/api/v1/edge/cameras/CAM-1/events/batch");
    assertThat(EdgeApi.modelArtifactPath("CAM-1", "det-1.3", EdgeApi.DETECTOR_ARTIFACT))
        .isEqualTo("/api/v1/edge/cameras/CAM-1/models/det-1.3/detector");
    assertThat(EdgeStomp.userDestination(EdgeStomp.CONFIG_QUEUE)).isEqualTo("/queue/config");
    EdgeConfiguration.Settings settings = EdgeConfiguration.Settings.defaults();
    assertThat(settings.heartbeatIntervalSeconds()).isEqualTo(5);
    assertThat(settings.detectionSampleIntervalMs()).isEqualTo(200);
    assertThat(settings.video()).isEqualTo(new EdgeConfiguration.LiveVideo(960, 540, 6, 0.7));
  }
}

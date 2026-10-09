package com.parkingwatch.backend.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parkingwatch.backend.camera.CameraDisplayStatus;
import com.parkingwatch.backend.model.ModelMetrics;
import com.parkingwatch.backend.model.ModelQualityGate;
import com.parkingwatch.backend.report.Report;
import com.parkingwatch.backend.report.ReportDraft;
import com.parkingwatch.backend.security.DeviceTokens;
import com.parkingwatch.backend.shared.InvalidStateTransitionException;
import com.parkingwatch.backend.shared.ValidationException;
import com.parkingwatch.backend.zone.ImagePoint;
import com.parkingwatch.backend.zone.ImagePolygon;
import com.parkingwatch.common.contract.EvidenceKeys;
import com.parkingwatch.common.domain.DismissalReason;
import com.parkingwatch.common.domain.PlateStatus;
import com.parkingwatch.common.domain.ReportStatus;
import com.parkingwatch.common.domain.VehicleType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainTest {

  private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
  private static final UUID REVIEWER = UUID.randomUUID();

  static Report newReport() {
    return new Report(
        new ReportDraft(
            UUID.randomUUID(),
            "CAM-MAQ-01",
            1,
            41,
            VehicleType.CAR,
            "ABC123",
            0.91,
            PlateStatus.READ,
            0.94,
            T0,
            T0.plusSeconds(12),
            new EvidenceKeys("evidence/a", "evidence/b", "evidence/c"),
            "det-1",
            UUID.randomUUID()));
  }

  @Test
  void reportStateMachineAllowsOnlyOneReview() {
    Report confirmed = newReport();
    confirmed.confirm(REVIEWER, T0);
    assertThat(confirmed.getStatus()).isEqualTo(ReportStatus.CONFIRMED);
    assertThatThrownBy(() -> confirmed.dismiss(REVIEWER, T0, DismissalReason.FALSE_POSITIVE))
        .isInstanceOf(InvalidStateTransitionException.class);

    Report dismissed = newReport();
    assertThatThrownBy(() -> dismissed.dismiss(REVIEWER, T0, null))
        .isInstanceOf(ValidationException.class);
    dismissed.dismiss(REVIEWER, T0, DismissalReason.FALSE_POSITIVE);
    assertThat(dismissed.getDismissalReason()).isEqualTo(DismissalReason.FALSE_POSITIVE);
    assertThat(dismissed.getReviewedBy()).isEqualTo(REVIEWER);
    assertThatThrownBy(() -> dismissed.confirm(REVIEWER, T0))
        .isInstanceOf(InvalidStateTransitionException.class);
  }

  @Test
  void reportRegistersTheExitOnce() {
    Report report = newReport();
    assertThat(report.dwellSeconds()).isEqualTo(12);
    assertThat(report.registerExit(T0.plusSeconds(90))).isTrue();
    assertThat(report.registerExit(T0.plusSeconds(120))).isFalse();
    assertThat(report.getTotalSeconds()).isEqualTo(90);
    assertThatThrownBy(() -> newReport().registerExit(T0.minusSeconds(1)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void cameraStatusIsDerivedFromTheLastSignal() {
    Duration timeout = Duration.ofSeconds(30);
    assertThat(CameraDisplayStatus.resolve(null, T0, false, timeout))
        .isEqualTo(CameraDisplayStatus.OFFLINE);
    assertThat(CameraDisplayStatus.resolve(T0.minusSeconds(31), T0, true, timeout))
        .isEqualTo(CameraDisplayStatus.OFFLINE);
    assertThat(CameraDisplayStatus.resolve(T0.minusSeconds(30), T0, false, timeout))
        .isEqualTo(CameraDisplayStatus.ONLINE);
    assertThat(CameraDisplayStatus.resolve(T0, T0, true, timeout))
        .isEqualTo(CameraDisplayStatus.ACTIVE_REPORT);
  }

  @Test
  void qualityGateEnforcesTheMinimumMetrics() {
    ModelQualityGate gate = new ModelQualityGate();
    ModelMetrics good = new ModelMetrics(0.92, 0.96, 0.91, 0.93);
    assertThat(gate.evaluate(good).passed()).isTrue();
    ModelQualityGate.Result bad = gate.evaluate(new ModelMetrics(0.92, 0.90, 0.91, 0.50));
    assertThat(bad.passed()).isFalse();
    assertThat(bad.failures()).hasSize(2);
    assertThat(gate.outperforms(new ModelMetrics(0.92, 0.96, 0.95, 0.93), good)).isTrue();
    assertThat(gate.outperforms(good, good)).isFalse();
    assertThat(new ModelMetrics(0, 0, 0, 0).f1()).isZero();
  }

  @Test
  void polygonValidationRejectsInvalidZones() {
    List<ImagePoint> square =
        List.of(
            new ImagePoint(100, 100),
            new ImagePoint(300, 100),
            new ImagePoint(300, 300),
            new ImagePoint(100, 300));
    ImagePolygon polygon = ImagePolygon.of(square, 1280, 720);
    assertThat(polygon.area()).isEqualTo(40_000);
    assertThat(polygon.contains(new ImagePoint(200, 200))).isTrue();
    assertThat(polygon.contains(new ImagePoint(50, 200))).isFalse();
    List<ImagePoint> bowTie =
        List.of(
            new ImagePoint(100, 100),
            new ImagePoint(300, 300),
            new ImagePoint(300, 100),
            new ImagePoint(100, 300));
    assertThatThrownBy(() -> ImagePolygon.of(bowTie, 1280, 720)).hasMessageContaining("cruzarse");
    assertThatThrownBy(() -> ImagePolygon.of(square.subList(0, 2), 1280, 720))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(
            () ->
                ImagePolygon.of(
                    List.of(new ImagePoint(0, 0), new ImagePoint(5, 0), new ImagePoint(5, 5)),
                    1280,
                    720))
        .hasMessageContaining("área");
    assertThatThrownBy(
            () ->
                ImagePolygon.of(
                    List.of(new ImagePoint(0, 0), new ImagePoint(2000, 0), new ImagePoint(0, 100)),
                    1280,
                    720))
        .hasMessageContaining("fuera del cuadro");
  }

  @Test
  void deviceTokensAreRandomAndOnlyTheirHashIsStored() {
    String token = DeviceTokens.generate();
    assertThat(token).startsWith("pwd_").hasSize(47);
    assertThat(DeviceTokens.generate()).isNotEqualTo(token);
    assertThat(DeviceTokens.hash(token)).hasSize(64).isEqualTo(DeviceTokens.hash(token));
  }
}

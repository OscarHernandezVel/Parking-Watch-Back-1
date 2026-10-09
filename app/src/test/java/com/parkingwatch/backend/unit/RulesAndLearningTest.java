package com.parkingwatch.backend.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkingwatch.backend.config.ParkingProperties;
import com.parkingwatch.backend.learning.Alert;
import com.parkingwatch.backend.learning.AlertService;
import com.parkingwatch.backend.learning.DriftMonitoringService;
import com.parkingwatch.backend.learning.RetrainingSampleRepository;
import com.parkingwatch.backend.learning.RetrainingService;
import com.parkingwatch.backend.report.Report;
import com.parkingwatch.backend.report.ReportRepository;
import com.parkingwatch.backend.rules.DuplicateStayRule;
import com.parkingwatch.backend.rules.EvidenceOwnershipRule;
import com.parkingwatch.backend.rules.MinimumConfidenceRule;
import com.parkingwatch.backend.rules.ReportCandidate;
import com.parkingwatch.backend.rules.ReportRule;
import com.parkingwatch.backend.rules.RuleOutcome;
import com.parkingwatch.backend.rules.SignaledZoneRule;
import com.parkingwatch.backend.rules.ToleranceReachedRule;
import com.parkingwatch.backend.rules.ZoneBelongsToCameraRule;
import com.parkingwatch.backend.storage.ObjectStorage;
import com.parkingwatch.backend.zone.ImagePoint;
import com.parkingwatch.backend.zone.NoParkingZone;
import com.parkingwatch.backend.zone.ZoneDraft;
import com.parkingwatch.common.contract.EdgeConfiguration;
import com.parkingwatch.common.contract.EvidenceKeys;
import com.parkingwatch.common.contract.ParkingEventMessage;
import com.parkingwatch.common.contract.PlateReading;
import com.parkingwatch.common.domain.DismissalReason;
import com.parkingwatch.common.domain.ParkingEventType;
import com.parkingwatch.common.domain.PlateStatus;
import com.parkingwatch.common.domain.VehicleType;
import com.parkingwatch.common.domain.ZoneType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RulesAndLearningTest {

  private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
  private static final String OWN = "evidence/CAM-MAQ-01/";

  private static NoParkingZone zone(boolean signaled, int tolerance) {
    List<ImagePoint> square =
        List.of(new ImagePoint(0, 0), new ImagePoint(100, 0), new ImagePoint(100, 100));
    return new NoParkingZone(
        "CAM-MAQ-01",
        new ZoneDraft(null, "Zona", ZoneType.YELLOW_ZONE, square, tolerance, signaled),
        T0);
  }

  private static ParkingEventMessage event(double dwell, double confidence, String prefix) {
    EvidenceKeys keys = new EvidenceKeys(prefix + "a.jpg", prefix + "b.jpg", prefix + "c.jpg");
    return new ParkingEventMessage(
        UUID.randomUUID(),
        ParkingEventType.TOLERANCE_EXCEEDED,
        1,
        7,
        VehicleType.CAR,
        confidence,
        "det-1",
        T0,
        T0.plusMillis((long) (dwell * 1000)),
        dwell,
        new PlateReading("ABC123", 0.9, PlateStatus.READ),
        keys);
  }

  private static String failingRule(
      ReportRule rules, String camera, ParkingEventMessage e, NoParkingZone z) {
    return rules.check(new ReportCandidate(camera, e, z)).rule();
  }

  @Test
  void theRuleChainAcceptsValidCandidatesAndExplainsRejections() {
    ReportRepository reports = mock(ReportRepository.class);
    when(reports.findByCameraIdAndZoneIdAndTrackIdAndEnteredAt(any(), anyLong(), anyLong(), any()))
        .thenReturn(Optional.empty());
    ReportRule rules =
        ReportRule.chainOf(
            List.of(
                new ZoneBelongsToCameraRule(),
                new SignaledZoneRule(),
                new ToleranceReachedRule(),
                new MinimumConfidenceRule(0.5),
                new EvidenceOwnershipRule(),
                new DuplicateStayRule(reports)));
    String cam = "CAM-MAQ-01";
    assertThat(
            rules.check(new ReportCandidate(cam, event(12, 0.9, OWN), zone(true, 10))).accepted())
        .isTrue();
    assertThat(failingRule(rules, cam, event(12, 0.9, OWN), null))
        .isEqualTo("ZoneBelongsToCameraRule");
    assertThat(failingRule(rules, "CAM-OTRA", event(12, 0.9, OWN), zone(true, 10)))
        .isEqualTo("ZoneBelongsToCameraRule");
    assertThat(failingRule(rules, cam, event(12, 0.9, OWN), zone(false, 10)))
        .isEqualTo("SignaledZoneRule");
    assertThat(failingRule(rules, cam, event(5, 0.9, OWN), zone(true, 10)))
        .isEqualTo("ToleranceReachedRule");
    assertThat(failingRule(rules, cam, event(12, 0.2, OWN), zone(true, 10)))
        .isEqualTo("MinimumConfidenceRule");
    assertThat(failingRule(rules, cam, event(12, 0.9, "evidence/CAM-OTRA/"), zone(true, 10)))
        .isEqualTo("EvidenceOwnershipRule");
    when(reports.findByCameraIdAndZoneIdAndTrackIdAndEnteredAt(any(), anyLong(), anyLong(), any()))
        .thenReturn(Optional.of(DomainTest.newReport()));
    RuleOutcome duplicate =
        rules.check(new ReportCandidate(cam, event(12, 0.9, OWN), zone(true, 10)));
    assertThat(duplicate.duplicate()).isTrue();
    assertThatThrownBy(() -> ReportRule.chainOf(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  static ParkingProperties properties(int threshold) {
    return new ParkingProperties(
        new ParkingProperties.Security(
            "x".repeat(40), Duration.ofMinutes(30), List.of(), true, 5, Duration.ofMinutes(15)),
        new ParkingProperties.Storage("/data"),
        new ParkingProperties.Devices(""),
        new ParkingProperties.Rules(0.5, Duration.ofSeconds(30)),
        new ParkingProperties.Retention(30),
        new ParkingProperties.Learning(threshold, 0.05, 100, 20),
        new ParkingProperties.Realtime(524_288, Duration.ofSeconds(10), 2_097_152),
        EdgeConfiguration.Settings.defaults());
  }

  @Test
  void retrainingCopiesFalsePositivesAndRaisesTheThresholdAlert() throws Exception {
    RetrainingSampleRepository samples = mock(RetrainingSampleRepository.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    AlertService alerts = mock(AlertService.class);
    when(samples.countByConsumedAtIsNull()).thenReturn(2L);
    RetrainingService service =
        new RetrainingService(
            samples,
            storage,
            alerts,
            new ObjectMapper(),
            Clock.fixed(T0, ZoneOffset.UTC),
            properties(2));
    Report report = DomainTest.newReport();
    report.dismiss(UUID.randomUUID(), T0, DismissalReason.FALSE_POSITIVE);
    assertThat(service.collect(report, null)).isTrue();
    verify(storage).copy("evidence/b", "retraining/false_positive/" + report.getId() + ".jpg");
    verify(alerts).raiseOnce(any(Alert.AlertDraft.class));
    Report momentary = DomainTest.newReport();
    momentary.dismiss(UUID.randomUUID(), T0, DismissalReason.MOMENTARY_STOP);
    assertThat(service.collect(momentary, null)).isFalse();
    assertThat(service.status().retrainingRequired()).isTrue();
  }

  @Test
  void driftMonitorAlertsOnHighDismissalRates() {
    ReportRepository reports = mock(ReportRepository.class);
    AlertService alerts = mock(AlertService.class);
    DriftMonitoringService monitor = new DriftMonitoringService(alerts, reports, properties(200));
    when(reports.recentReviewOutcomes(anyInt())).thenReturn(outcomes(10, 5));
    assertThat(monitor.checkDismissalRate()).isEqualTo(OptionalDouble.empty());
    when(reports.recentReviewOutcomes(anyInt())).thenReturn(outcomes(100, 3));
    assertThat(monitor.checkDismissalRate().getAsDouble()).isEqualTo(0.03);
    verify(alerts, never()).raiseOnce(any());
    when(reports.recentReviewOutcomes(anyInt())).thenReturn(outcomes(100, 9));
    monitor.checkDismissalRate();
    verify(alerts).raiseOnce(any(Alert.AlertDraft.class));
  }

  private static ReportRepository.ReviewOutcomes outcomes(long reviewed, long dismissed) {
    return new ReportRepository.ReviewOutcomes() {
      @Override
      public long getReviewed() {
        return reviewed;
      }

      @Override
      public long getDismissed() {
        return dismissed;
      }
    };
  }
}

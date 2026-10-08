package com.parkingwatch.backend.report;

import com.parkingwatch.backend.shared.InvalidStateTransitionException;
import com.parkingwatch.common.domain.DismissalReason;
import com.parkingwatch.common.domain.ReportStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Patrón State: cada estado decide qué transiciones admite el reporte. Nuevo -> Confirmado o
 * Descartado; Confirmado y Descartado son estados finales.
 */
abstract class ReportState {

  private static final Map<ReportStatus, ReportState> STATES =
      Map.of(
          ReportStatus.NEW, new NewState(),
          ReportStatus.CONFIRMED, new FinalState(ReportStatus.CONFIRMED),
          ReportStatus.DISMISSED, new FinalState(ReportStatus.DISMISSED));

  static ReportState of(ReportStatus status) {
    return STATES.get(status);
  }

  abstract ReportStatus status();

  void confirm(Report report, UUID reviewerId, Instant at) {
    throw new InvalidStateTransitionException(status().name(), "confirm");
  }

  void dismiss(Report report, UUID reviewerId, Instant at, DismissalReason reason) {
    throw new InvalidStateTransitionException(status().name(), "dismiss");
  }

  /** Estado inicial: admite confirmar o descartar. */
  private static final class NewState extends ReportState {

    @Override
    ReportStatus status() {
      return ReportStatus.NEW;
    }

    @Override
    void confirm(Report report, UUID reviewerId, Instant at) {
      report.applyReview(ReportStatus.CONFIRMED, null, reviewerId, at);
    }

    @Override
    void dismiss(Report report, UUID reviewerId, Instant at, DismissalReason reason) {
      report.applyReview(ReportStatus.DISMISSED, reason, reviewerId, at);
    }
  }

  /** Estados finales: rechazan cualquier revisión adicional. */
  private static final class FinalState extends ReportState {

    private final ReportStatus status;

    private FinalState(ReportStatus status) {
      this.status = status;
    }

    @Override
    ReportStatus status() {
      return status;
    }
  }
}

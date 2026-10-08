package com.parkingwatch.backend.rules;

import com.parkingwatch.backend.edge.EvidenceService;
import com.parkingwatch.common.contract.EvidenceKeys;
import java.util.stream.Stream;

/** Las evidencias deben estar bajo el prefijo de la cámara que reporta (control de acceso). */
public class EvidenceOwnershipRule extends ReportRule {

  @Override
  protected RuleOutcome evaluate(ReportCandidate candidate) {
    EvidenceKeys evidence = candidate.event().evidence();
    boolean owned =
        Stream.of(evidence.entryPhotoKey(), evidence.reportPhotoKey(), evidence.platePhotoKey())
            .allMatch(key -> EvidenceService.belongsTo(candidate.cameraId(), key));
    return owned ? RuleOutcome.ok() : reject("Las evidencias no pertenecen a la cámara");
  }
}

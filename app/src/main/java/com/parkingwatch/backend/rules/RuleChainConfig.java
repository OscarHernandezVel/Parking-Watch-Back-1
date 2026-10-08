package com.parkingwatch.backend.rules;

import com.parkingwatch.backend.config.ParkingProperties;
import com.parkingwatch.backend.report.ReportRepository;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fábrica de la cadena de reglas. El orden importa: primero las reglas que se evalúan en memoria y
 * al final la que consulta la base de datos.
 */
@Configuration(proxyBeanMethods = false)
public class RuleChainConfig {

  @Bean
  ReportRule reportRuleChain(ReportRepository reports, ParkingProperties properties) {
    return ReportRule.chainOf(
        List.of(
            new ZoneBelongsToCameraRule(),
            new SignaledZoneRule(),
            new ToleranceReachedRule(),
            new MinimumConfidenceRule(properties.rules().minDetectionConfidence()),
            new EvidenceOwnershipRule(),
            new DuplicateStayRule(reports)));
  }
}

package com.parkingwatch.common.contract;

import java.util.List;

/** Resultados de un lote de eventos, en el orden en que se procesaron. */
public record EventBatchResult(List<EventResult> results) {

  /** Copia defensiva de la lista. */
  public EventBatchResult {
    results = List.copyOf(results);
  }
}

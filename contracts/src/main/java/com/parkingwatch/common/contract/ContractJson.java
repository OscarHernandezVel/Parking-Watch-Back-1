package com.parkingwatch.common.contract;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Configuración JSON común del contrato: fechas ISO-8601 y tolerancia a campos nuevos. */
public final class ContractJson {

  private ContractJson() {}

  /** Crea un ObjectMapper configurado para el contrato. */
  public static ObjectMapper newMapper() {
    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
  }
}

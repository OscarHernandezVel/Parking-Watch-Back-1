package com.parkingwatch.backend.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj inyectable: las pruebas lo reemplazan para controlar tolerancias y vencimientos. */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}

package com.parkingwatch.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Punto de entrada del backend del sistema de reconocimiento de parqueo no autorizado. */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class BackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, args);
  }
}

package com.parkingwatch.backend.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Infraestructura compartida (patrón Singleton Containers): PostgreSQL 17 con PostGIS, como Render
 * Postgres, y una carpeta temporal que hace las veces del disco persistente {@code /data}. Se
 * inician una sola vez para todas las pruebas de integración.
 */
public final class Containers {

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("cupo")
          .withUsername("cupo")
          .withPassword("cupo");

  /** Disco persistente simulado. */
  public static final Path STORAGE = createStorage();

  static {
    POSTGRES.start();
  }

  private Containers() {}

  /** Publica las conexiones en la configuración de Spring. */
  public static void register(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("parking.storage.path", STORAGE::toString);
  }

  /** Ruta de un archivo guardado en el disco simulado. */
  public static Path stored(String key) {
    return STORAGE.resolve(key);
  }

  private static Path createStorage() {
    try {
      return Files.createTempDirectory("cupo-data");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

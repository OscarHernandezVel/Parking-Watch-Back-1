package com.parkingwatch.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockPropertySource;

/** Conversión de la DATABASE_URL de Render Postgres a la configuración JDBC de Spring. */
class RenderDatabaseUrlTest {

  @Test
  void renderUrlsAreConvertedToJdbc() {
    Map<String, Object> properties =
        RenderDatabaseUrl.convert(
                "postgresql://cupo_user:cl%40ve@dpg-abc-a.virginia-postgres.render.com/cupo?sslmode=require")
            .orElseThrow();
    assertThat(properties)
        .containsEntry(
            "spring.datasource.url",
            "jdbc:postgresql://dpg-abc-a.virginia-postgres.render.com:5432/cupo?sslmode=require")
        .containsEntry("spring.datasource.username", "cupo_user")
        .containsEntry("spring.datasource.password", "cl@ve");
    assertThat(RenderDatabaseUrl.convert("postgres://u@db:6543/x").orElseThrow())
        .containsEntry("spring.datasource.url", "jdbc:postgresql://db:6543/x")
        .doesNotContainKey("spring.datasource.password");
  }

  @Test
  void jdbcUrlsAndMissingValuesAreLeftUntouched() {
    assertThat(RenderDatabaseUrl.convert("jdbc:postgresql://localhost/cupo")).isEmpty();
    assertThat(RenderDatabaseUrl.convert(null)).isEmpty();
  }

  @Test
  void theConvertedPropertiesTakePrecedence() {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addLast(new MockPropertySource().withProperty("DATABASE_URL", "postgresql://a:b@h/db"));
    new RenderDatabaseUrl().postProcessEnvironment(environment, new SpringApplication());
    assertThat(environment.getProperty("spring.datasource.url"))
        .isEqualTo("jdbc:postgresql://h:5432/db");
    assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("b");
  }
}

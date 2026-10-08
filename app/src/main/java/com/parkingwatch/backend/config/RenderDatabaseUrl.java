package com.parkingwatch.backend.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Render entrega la cadena de conexión de Render Postgres como {@code
 * postgresql://usuario:clave@host:puerto/base}. Este procesador la convierte, antes de crear el
 * pool de conexiones, en la URL JDBC y las credenciales que espera Spring. Una URL que ya empiece
 * por {@code jdbc:} se deja intacta (desarrollo local y pruebas).
 */
public class RenderDatabaseUrl implements EnvironmentPostProcessor {

  private static final String VARIABLE = "DATABASE_URL";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    convert(environment.getProperty(VARIABLE))
        .ifPresent(
            properties ->
                environment
                    .getPropertySources()
                    .addFirst(new MapPropertySource("renderDatabaseUrl", properties)));
  }

  /** Propiedades de Spring equivalentes a la URL de Render, o vacío si no aplica. */
  static Optional<Map<String, Object>> convert(String url) {
    if (url == null || !(url.startsWith("postgres://") || url.startsWith("postgresql://"))) {
      return Optional.empty();
    }
    URI uri = URI.create(url);
    Map<String, Object> properties = new HashMap<>();
    int port = uri.getPort() < 0 ? 5432 : uri.getPort();
    String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
    properties.put(
        "spring.datasource.url",
        "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath() + query);
    String userInfo = uri.getRawUserInfo();
    if (userInfo != null) {
      int separator = userInfo.indexOf(':');
      String user = separator < 0 ? userInfo : userInfo.substring(0, separator);
      properties.put("spring.datasource.username", decode(user));
      if (separator >= 0) {
        properties.put("spring.datasource.password", decode(userInfo.substring(separator + 1)));
      }
    }
    return Optional.of(properties);
  }

  private static String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }
}

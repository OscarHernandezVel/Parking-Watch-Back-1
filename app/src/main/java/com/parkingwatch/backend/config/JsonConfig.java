package com.parkingwatch.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hibernate serializa las columnas JSONB (polígonos y estado en vivo) con el mismo ObjectMapper de
 * Spring, que conoce los tipos de fecha de Java 8+.
 */
@Configuration(proxyBeanMethods = false)
public class JsonConfig {

  @Bean
  HibernatePropertiesCustomizer jsonFormatMapper(ObjectMapper objectMapper) {
    return properties ->
        properties.put(
            AvailableSettings.JSON_FORMAT_MAPPER, new JacksonJsonFormatMapper(objectMapper));
  }
}

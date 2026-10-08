package com.parkingwatch.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contrato OpenAPI de la API REST (RNF-6.1). El Frontend genera sus tipos TypeScript a partir de él
 * (openapi-typescript); los mensajes WebSocket se documentan aparte con AsyncAPI.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

  private static final String JWT = "jwt";
  private static final String DEVICE = "deviceToken";

  @Bean
  OpenAPI cupoOpenApi(@Value("${cupo.version:1.0.0}") String version) {
    return new OpenAPI()
        .info(
            new Info()
                .title("Cupo - API del Backend")
                .version(version)
                .description(
                    "Reconocimiento de parqueo en zonas no autorizadas: cámaras, zonas, reportes,"
                        + " modelos, estadísticas y API del Edge. Tiempo real por WebSocket STOMP"
                        + " (ver AsyncAPI)."))
        .components(
            new Components()
                .addSecuritySchemes(
                    JWT,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSecuritySchemes(
                    DEVICE,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("Token del dispositivo Edge (pwd_...)")))
        .addSecurityItem(new SecurityRequirement().addList(JWT));
  }
}

package com.parkingwatch.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.parkingwatch.backend.security.DeviceAuthenticationFilter;
import com.parkingwatch.backend.security.DeviceRegistry;
import com.parkingwatch.backend.security.JwtAuthorities;
import com.parkingwatch.backend.security.JwtTokenService;
import com.parkingwatch.backend.security.ProblemResponses;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Seguridad (PB-05, RNF-4.1, RNF-4.3): dos cadenas de filtros sin estado.
 *
 * <ul>
 *   <li>Edge ({@code /api/v1/edge/**}): token de dispositivo por cámara.
 *   <li>Funcionarios: JWT HS256 de corta duración emitido por el login, con roles Operador y
 *       Administrador; el administrador necesita segundo factor ({@link JwtAuthorities}).
 * </ul>
 *
 * <p>Los endpoints WebSocket ({@code /ws} y {@code /ws/edge}) se abren sin autenticación HTTP: el
 * JWT o el token del dispositivo se validan en la trama CONNECT de STOMP ({@code
 * StompAuthenticationInterceptor}).
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

  private static final String[] PUBLIC_PATHS = {
    "/api/v1/auth/login",
    "/ws",
    "/ws/**",
    "/actuator/health/**",
    "/actuator/info",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/error"
  };

  @Bean
  @Order(1)
  SecurityFilterChain edgeChain(HttpSecurity http, DeviceRegistry registry, ObjectMapper mapper)
      throws Exception {
    return http.securityMatcher("/api/v1/edge/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> auth.anyRequest().hasAuthority(DeviceAuthenticationFilter.DEVICE_ROLE))
        .addFilterBefore(
            new DeviceAuthenticationFilter(registry, mapper),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain apiChain(
      HttpSecurity http, ObjectMapper mapper, JwtAuthenticationConverter jwtConverter)
      throws Exception {
    // API sin sesiones ni cookies: el token viaja en el encabezado Authorization, sin riesgo de
    // CSRF.
    return http.csrf(csrf -> csrf.disable())
        .cors(cors -> {})
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(PUBLIC_PATHS)
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .hasRole("ADMINISTRATOR")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, ex) ->
                            ProblemResponses.write(
                                mapper,
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "Sesión inválida o ausente"))
                    .accessDeniedHandler(
                        (request, response, ex) ->
                            ProblemResponses.write(
                                mapper,
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                "FORBIDDEN",
                                "No tiene permisos para esta operación")))
        .build();
  }

  /** El administrador hereda los permisos del operador (RF-5.1). */
  @Bean
  static RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy("ROLE_ADMINISTRATOR > ROLE_OPERATOR");
  }

  /** bcrypt por defecto, con prefijo de algoritmo para migrar a Argon2 sin romper usuarios. */
  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  JwtEncoder jwtEncoder(ParkingProperties properties) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
  }

  /** Valida firma, emisor y vigencia con el mismo reloj con el que se emiten los tokens. */
  @Bean
  JwtDecoder jwtDecoder(ParkingProperties properties, Clock clock) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(secretKey(properties))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    JwtTimestampValidator expiry = new JwtTimestampValidator(Duration.ofSeconds(30));
    expiry.setClock(clock);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            expiry, new JwtIssuerValidator(JwtTokenService.ISSUER)));
    return decoder;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(ParkingProperties properties) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(properties.security().corsOrigins());
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    cors.setExposedHeaders(List.of("Content-Disposition"));
    cors.setMaxAge(600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/v1/**", cors);
    return source;
  }

  /** Convierte el JWT en permisos; lo comparten la API REST y el WebSocket de los navegadores. */
  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter(ParkingProperties properties) {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        new JwtAuthorities(properties.security().adminMfaRequired()));
    return converter;
  }

  private static SecretKey secretKey(ParkingProperties properties) {
    byte[] bytes = properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8);
    return new SecretKeySpec(bytes, "HmacSHA256");
  }
}

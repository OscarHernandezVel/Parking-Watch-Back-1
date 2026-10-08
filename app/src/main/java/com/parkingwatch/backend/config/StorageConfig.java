package com.parkingwatch.backend.config;

import com.parkingwatch.backend.storage.FileSystemObjectStorage;
import com.parkingwatch.backend.storage.ObjectStorage;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Almacenamiento de fotos de evidencia y modelos ONNX en el disco persistente de Render, montado en
 * {@code STORAGE_PATH} ({@code /data} en producción).
 */
@Configuration(proxyBeanMethods = false)
public class StorageConfig {

  @Bean
  ObjectStorage objectStorage(ParkingProperties properties) throws IOException {
    return new FileSystemObjectStorage(Path.of(properties.storage().path()));
  }
}

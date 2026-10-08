package com.parkingwatch.backend.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Almacenamiento en el disco persistente de Render (montado en {@code /data}). Las escrituras son
 * atómicas (archivo temporal y renombrado) y las llaves se validan para que ninguna ruta salga de
 * la carpeta raíz (OWASP A01: path traversal). Por usar disco, el servicio corre como una sola
 * instancia, suficiente para esta fase.
 */
public class FileSystemObjectStorage implements ObjectStorage {

  private static final Pattern VALID_KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]{0,511}$");

  private final Path root;

  public FileSystemObjectStorage(Path root) throws IOException {
    this.root = Files.createDirectories(root).toRealPath();
  }

  @Override
  public long put(String key, InputStream content) throws IOException {
    Path target = resolve(key);
    Files.createDirectories(Objects.requireNonNull(target.getParent(), "carpeta"));
    Path temp = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
    try {
      long size = Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      return size;
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Override
  public InputStream open(String key) throws IOException {
    return Files.newInputStream(resolve(key));
  }

  @Override
  public boolean exists(String key) {
    return isValid(key) && Files.isRegularFile(resolve(key));
  }

  @Override
  public long size(String key) throws IOException {
    return Files.size(resolve(key));
  }

  @Override
  public void copy(String sourceKey, String targetKey) throws IOException {
    try (InputStream source = open(sourceKey)) {
      put(targetKey, source);
    }
  }

  @Override
  public void putJson(String key, String json) throws IOException {
    put(key, new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }

  @Override
  public void deleteAll(Collection<String> keys) throws IOException {
    for (String key : keys) {
      if (isValid(key)) {
        Files.deleteIfExists(resolve(key));
      }
    }
  }

  /** Indica si la llave tiene un formato permitido (sin {@code ..} ni rutas absolutas). */
  public static boolean isValid(String key) {
    return key != null && VALID_KEY.matcher(key).matches() && !key.contains("..");
  }

  private Path resolve(String key) {
    if (!isValid(key)) {
      throw new IllegalArgumentException("Llave de archivo inválida: " + key);
    }
    Path path = root.resolve(key).normalize();
    if (!path.startsWith(root)) {
      throw new IllegalArgumentException("Llave fuera del almacenamiento: " + key);
    }
    return path;
  }
}

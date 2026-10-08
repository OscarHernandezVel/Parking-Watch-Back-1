package com.parkingwatch.backend.edge;

import com.parkingwatch.backend.shared.ValidationException;
import com.parkingwatch.backend.storage.ObjectStorage;
import com.parkingwatch.common.contract.EvidenceKeys;
import com.parkingwatch.common.domain.EvidenceKind;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Guarda en el disco persistente de Render las fotos de evidencia que sube el Edge por HTTPS
 * (RF-7.3, RF-8.3): foto al entrar, foto al cumplirse la tolerancia y recorte de la placa. Solo se
 * aceptan JPEG de hasta 2 MB y se guardan bajo el prefijo de la cámara que los sube.
 */
@Service
public class EvidenceService {

  /** Tamaño máximo de una foto de evidencia (2 MB). */
  public static final long MAX_SIZE_BYTES = 2_000_000;

  private static final DateTimeFormatter DATE_PATH =
      DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC);
  private static final int JPEG_MARKER = 0xFF;
  private static final int JPEG_START = 0xD8;

  private final ObjectStorage storage;
  private final Clock clock;

  public EvidenceService(ObjectStorage storage, Clock clock) {
    this.storage = storage;
    this.clock = clock;
  }

  /** Fotos de un reporte recibidas en la subida multipart. */
  public record EvidencePhotos(MultipartFile entry, MultipartFile report, MultipartFile plate) {}

  /** Prefijo de las evidencias de una cámara. */
  public static String prefix(String cameraId) {
    return "evidence/" + cameraId + "/";
  }

  /** Un Edge solo puede referenciar evidencias bajo el prefijo de su propia cámara. */
  public static boolean belongsTo(String cameraId, String objectKey) {
    return objectKey != null && objectKey.startsWith(prefix(cameraId)) && !objectKey.contains("..");
  }

  /** Valida y guarda las tres fotos; retorna sus rutas para el evento de tolerancia superada. */
  public EvidenceKeys store(String cameraId, EvidencePhotos photos) {
    String base = prefix(cameraId) + DATE_PATH.format(clock.instant()) + "/" + UUID.randomUUID();
    return new EvidenceKeys(
        save(base, EvidenceKind.ENTRY, photos.entry()),
        save(base, EvidenceKind.REPORT, photos.report()),
        save(base, EvidenceKind.PLATE, photos.plate()));
  }

  /** Indica si la evidencia referenciada por un evento ya está guardada. */
  public boolean exists(String key) {
    return storage.exists(key);
  }

  private String save(String base, EvidenceKind kind, MultipartFile file) {
    validate(kind, file);
    String key = base + "-" + kind.name().toLowerCase(Locale.ROOT) + ".jpg";
    try (InputStream input = file.getInputStream()) {
      storage.put(key, input);
      return key;
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo guardar la evidencia " + key, e);
    }
  }

  private static void validate(EvidenceKind kind, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ValidationException("Falta la foto " + kind);
    }
    if (file.getSize() > MAX_SIZE_BYTES) {
      throw new ValidationException("La foto " + kind + " supera 2 MB");
    }
    if (!isJpeg(file)) {
      throw new ValidationException("La foto " + kind + " no es un JPEG");
    }
  }

  private static boolean isJpeg(MultipartFile file) {
    try (InputStream input = file.getInputStream()) {
      return input.read() == JPEG_MARKER && input.read() == JPEG_START;
    } catch (IOException e) {
      return false;
    }
  }
}

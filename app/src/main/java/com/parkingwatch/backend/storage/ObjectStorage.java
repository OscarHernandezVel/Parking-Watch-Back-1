package com.parkingwatch.backend.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * Puerto de almacenamiento de archivos: fotos de evidencia, dataset de reentrenamiento y modelos
 * ONNX. Estructura: {@code evidence/}, {@code retraining/} y {@code models/}. En Render lo
 * implementa el disco persistente montado en {@code /data} (RF-7.3, RF-9.1).
 */
public interface ObjectStorage {

  /** Guarda (o reemplaza) un archivo y retorna su tamaño en bytes. */
  long put(String key, InputStream content) throws IOException;

  /** Abre un archivo para lectura; el llamador debe cerrarlo. */
  InputStream open(String key) throws IOException;

  boolean exists(String key);

  /** Tamaño del archivo en bytes. */
  long size(String key) throws IOException;

  void copy(String sourceKey, String targetKey) throws IOException;

  void putJson(String key, String json) throws IOException;

  /** Borra los archivos que existan; ignora los que ya no están. */
  void deleteAll(Collection<String> keys) throws IOException;
}

package com.parkingwatch.backend.model;

import com.parkingwatch.common.contract.EdgeApi;
import java.util.Arrays;
import java.util.Optional;

/** Archivos ONNX de una versión del modelo: detector de vehículos y lector de placas. */
public enum ModelArtifactKind {
  DETECTOR(EdgeApi.DETECTOR_ARTIFACT),
  PLATE_READER(EdgeApi.PLATE_READER_ARTIFACT);

  private final String pathName;

  ModelArtifactKind(String pathName) {
    this.pathName = pathName;
  }

  /** Nombre en las rutas de la API ({@code detector} o {@code plate-reader}). */
  public String pathName() {
    return pathName;
  }

  /** Busca el tipo por su nombre en la ruta. */
  public static Optional<ModelArtifactKind> fromPath(String value) {
    return Arrays.stream(values()).filter(kind -> kind.pathName.equals(value)).findFirst();
  }

  /** Ruta del archivo en el disco persistente. */
  public String storageKey(String version) {
    return "models/" + version + "/" + pathName + ".onnx";
  }
}

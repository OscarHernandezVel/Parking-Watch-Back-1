package com.parkingwatch.common.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Rutas, en el disco persistente del backend, de las fotos de evidencia ya subidas por el Edge
 * (RF-7.3). Es la respuesta de la subida multipart ({@link EdgeApi#EVIDENCE}).
 */
public record EvidenceKeys(
    @NotBlank @Size(max = 512) String entryPhotoKey,
    @NotBlank @Size(max = 512) String reportPhotoKey,
    @NotBlank @Size(max = 512) String platePhotoKey) {}

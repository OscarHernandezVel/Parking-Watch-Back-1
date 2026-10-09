package com.parkingwatch.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkingwatch.common.contract.EdgeApi;
import com.parkingwatch.common.contract.EvidenceKeys;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Sube fotos de evidencia como lo hace el Edge: una petición multipart por HTTPS (RF-8.3). */
public final class EvidenceUploads {

  public static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3, (byte) 0xFF, (byte) 0xD9};

  private EvidenceUploads() {}

  /** Retorna las rutas de las tres fotos subidas. */
  public static EvidenceKeys upload(MockMvc mvc, ObjectMapper mapper) throws Exception {
    String body =
        mvc.perform(
                multipart(EdgeApi.pathFor(IntegrationTest.CAMERA_ID, EdgeApi.EVIDENCE))
                    .file(photo("entry"))
                    .file(photo("report"))
                    .file(photo("plate"))
                    .header("Authorization", "Bearer " + IntegrationTest.DEVICE_TOKEN))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readValue(body, EvidenceKeys.class);
  }

  /** Parte multipart con una foto JPEG mínima. */
  public static MockMultipartFile photo(String name) {
    return new MockMultipartFile(name, name + ".jpg", "image/jpeg", JPEG);
  }
}

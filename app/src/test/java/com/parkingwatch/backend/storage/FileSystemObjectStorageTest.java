package com.parkingwatch.backend.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Disco persistente de Render: escrituras atómicas y llaves sin path traversal. */
class FileSystemObjectStorageTest {

  @TempDir Path root;

  @Test
  void filesAreWrittenReadCopiedAndDeleted() throws Exception {
    FileSystemObjectStorage storage = new FileSystemObjectStorage(root.resolve("data"));
    byte[] content = {1, 2, 3};
    assertThat(storage.put("evidence/CAM-1/a.jpg", new ByteArrayInputStream(content))).isEqualTo(3);
    assertThat(storage.exists("evidence/CAM-1/a.jpg")).isTrue();
    assertThat(storage.size("evidence/CAM-1/a.jpg")).isEqualTo(3);
    try (InputStream input = storage.open("evidence/CAM-1/a.jpg")) {
      assertThat(input.readAllBytes()).isEqualTo(content);
    }
    storage.copy("evidence/CAM-1/a.jpg", "retraining/a.jpg");
    storage.putJson("retraining/a.json", "{\"ok\":true}");
    assertThat(Files.readString(root.resolve("data/retraining/a.json"), StandardCharsets.UTF_8))
        .contains("ok");
    storage.deleteAll(List.of("evidence/CAM-1/a.jpg", "no/existe.jpg", "../fuera"));
    assertThat(storage.exists("evidence/CAM-1/a.jpg")).isFalse();
    assertThat(storage.exists("retraining/a.jpg")).isTrue();
    try (var files = Files.list(root.resolve("data/retraining"))) {
      assertThat(files.map(Path::toString)).noneMatch(name -> name.endsWith(".tmp"));
    }
  }

  @Test
  void keysCannotEscapeTheStorageFolder() throws Exception {
    FileSystemObjectStorage storage = new FileSystemObjectStorage(root);
    assertThat(FileSystemObjectStorage.isValid("../etc/passwd")).isFalse();
    assertThat(FileSystemObjectStorage.isValid("/etc/passwd")).isFalse();
    assertThat(FileSystemObjectStorage.isValid(null)).isFalse();
    assertThat(storage.exists("../x")).isFalse();
    assertThatThrownBy(() -> storage.open("a/../../x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> storage.put("C:/x", new ByteArrayInputStream(new byte[0])))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

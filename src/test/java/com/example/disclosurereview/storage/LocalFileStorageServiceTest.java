package com.example.disclosurereview.storage;

import com.example.disclosurereview.config.ReviewProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void savesPdfWithHashAndOpaqueStorageKey() {
        ReviewProperties properties = new ReviewProperties();
        properties.getStorage().setRootDirectory(tempDir.toString());
        LocalFileStorageService service = new LocalFileStorageService(properties);

        StoredFile stored = service.save(new ByteArrayInputStream("%PDF-1.4\n%%EOF".getBytes()),
                "../原始文件.pdf");

        assertThat(stored.sha256()).hasSize(64);
        assertThat(stored.storageKey()).doesNotContain("..");
        assertThat(service.exists(stored.storageKey())).isTrue();
    }

    @Test
    void rejectsPathTraversalOnLoad() {
        ReviewProperties properties = new ReviewProperties();
        properties.getStorage().setRootDirectory(tempDir.toString());
        LocalFileStorageService service = new LocalFileStorageService(properties);

        assertThatThrownBy(() -> service.load("../secret.pdf"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("非法文件路径");
    }
}

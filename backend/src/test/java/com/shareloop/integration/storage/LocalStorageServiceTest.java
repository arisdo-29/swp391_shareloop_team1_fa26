package com.shareloop.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageServiceTest {
    @TempDir
    Path directory;

    @Test
    void savesReadsAndDeletesInsideConfiguredDirectory() throws Exception {
        LocalStorageService storage = new LocalStorageService(directory.toString());
        byte[] bytes = {1, 2, 3};
        String key = storage.save(bytes, "png");
        assertThat(storage.read(key)).containsExactly(bytes);
        storage.delete(key);
        assertThatThrownBy(() -> storage.read(key)).isInstanceOf(java.nio.file.NoSuchFileException.class);
    }

    @Test
    void rejectsPathTraversal() {
        LocalStorageService storage = new LocalStorageService(directory.toString());
        assertThatThrownBy(() -> storage.read("../outside")).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.shareloop.integration.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Lưu ảnh vào thư mục trên máy chạy backend. Dùng khi app.storage.type = local (mặc định). */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {
    private final Path root;

    public LocalStorageService(@Value("${app.storage.local-dir:./uploads}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String save(byte[] content, String extension) throws IOException {
        Files.createDirectories(root);
        String key = UUID.randomUUID() + "." + extension;
        Files.write(resolve(key), content);
        return key;
    }

    @Override
    public byte[] read(String key) throws IOException {
        return Files.readAllBytes(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}

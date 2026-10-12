package com.shareloop.integration.storage;

import java.io.IOException;

public interface StorageService {
    String save(byte[] content, String extension) throws IOException;

    byte[] read(String key) throws IOException;

    void delete(String key) throws IOException;
}

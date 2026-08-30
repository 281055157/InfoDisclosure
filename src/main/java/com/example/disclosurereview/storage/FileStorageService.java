package com.example.disclosurereview.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;

public interface FileStorageService {

    StoredFile save(InputStream inputStream, String originalFileName);

    Resource load(String storageKey);

    void delete(String storageKey);

    boolean exists(String storageKey);
}

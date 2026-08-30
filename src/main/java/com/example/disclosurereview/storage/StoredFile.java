package com.example.disclosurereview.storage;

public record StoredFile(
        String originalFileName,
        String storedFileName,
        String storageKey,
        String filePath,
        String sha256,
        long size
) {
}

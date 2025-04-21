package dev.boissin.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FileItem (
    String id,
    String importId,
    String fileType,
    String filePath,
    Instant timestamp
) implements Serializable {

    public FileItem {
        Objects.requireNonNull(id, "importId cannot be null");
        Objects.requireNonNull(importId, "importId cannot be null");
        Objects.requireNonNull(fileType, "fileType cannot be null");
        Objects.requireNonNull(filePath, "filePath cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    public FileItem(String importId, String fileType, String filePath) {
        this(UUID.randomUUID().toString(), importId, fileType, filePath, Instant.now());
    }

}

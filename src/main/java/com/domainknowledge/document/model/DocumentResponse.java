package com.domainknowledge.document.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** O documento como a API devolve (plan §4). Não expõe a chave interna do storage. */
@Builder
public record DocumentResponse(
        UUID id,
        String title,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String sha256,
        int version,
        DocumentStatus status,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {

    public static DocumentResponse from(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .originalFilename(document.getOriginalFilename())
                .contentType(document.getContentType())
                .sizeBytes(document.getSizeBytes())
                .sha256(document.getSha256())
                .version(document.getVersion())
                .status(document.getStatus())
                .errorMessage(document.getErrorMessage())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}

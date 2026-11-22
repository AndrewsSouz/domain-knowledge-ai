package com.domainknowledge.ingestion.model;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Tipos de arquivo aceitos no upload (RF1). */
@Getter
@RequiredArgsConstructor
public enum DocumentType {

    PDF("application/pdf", "pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");

    /** Media type (o antigo "MIME type") que o Tika devolve. */
    private final String mediaType;

    /** Extensão usada na chave do storage: {@code documents/{id}/v1/original.{extension}}. */
    private final String extension;

    public static Optional<DocumentType> fromMediaType(String mediaType) {
        return Arrays.stream(values())
                .filter(type -> type.mediaType.equals(mediaType))
                .findFirst();
    }
}

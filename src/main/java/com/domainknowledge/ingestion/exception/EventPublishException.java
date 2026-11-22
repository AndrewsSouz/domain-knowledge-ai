package com.domainknowledge.ingestion.exception;

import java.util.UUID;
import lombok.Getter;

/**
 * O Kafka não confirmou o evento a tempo. O documento fica em
 * {@code RECEIVED} e a API responde {@code 503} com o {@code documentId}
 * (RF8); reenviar o mesmo arquivo tenta de novo (RF2).
 */
@Getter
public class EventPublishException extends RuntimeException {

    private final UUID documentId;

    public EventPublishException(UUID documentId, Throwable cause) {
        super("O Kafka não confirmou o evento do documento " + documentId, cause);
        this.documentId = documentId;
    }
}

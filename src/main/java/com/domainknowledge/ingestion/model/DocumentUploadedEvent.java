package com.domainknowledge.ingestion.model;

import com.domainknowledge.document.model.Document;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * Evento publicado depois que o original está no storage e o documento está
 * no banco (arquitetura §5). Leva só a referência ao arquivo, nunca o
 * conteúdo (ADR 0004). É o contrato entre o upload e a indexação.
 *
 * @param eventId pra rastreio e idempotência
 * @param sha256  o consumidor confere se o arquivo baixado é o mesmo que subiu
 */
@Builder
public record DocumentUploadedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID documentId,
        int version,
        String storageKey,
        String contentType,
        String sha256) {

    /** Tópico do evento; a chave da mensagem é o {@code documentId}. */
    public static final String TOPIC = "document-uploaded";

    public static final String EVENT_TYPE = "DOCUMENT_UPLOADED";

    public static DocumentUploadedEvent of(Document document, Instant now) {
        return DocumentUploadedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(EVENT_TYPE)
                .occurredAt(now)
                .documentId(document.getId())
                .version(document.getVersion())
                .storageKey(document.getStorageKey())
                .contentType(document.getContentType())
                .sha256(document.getSha256())
                .build();
    }
}

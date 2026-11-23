package com.domainknowledge.ingestion;

import com.domainknowledge.ingestion.model.DocumentUploadedEvent;
import java.time.Instant;
import java.util.UUID;

/** Dados de teste para a ingestão. */
public final class IngestionFixtures {

    private IngestionFixtures() {
    }

    /** Evento completo de um documento novo, com horário fixo. */
    public static DocumentUploadedEvent uploadedEvent() {
        UUID documentId = UUID.randomUUID();
        return DocumentUploadedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(DocumentUploadedEvent.EVENT_TYPE)
                .occurredAt(Instant.parse("2026-11-22T14:03:00Z"))
                .documentId(documentId)
                .version(1)
                .storageKey("documents/" + documentId + "/v1/original.pdf")
                .contentType("application/pdf")
                .sha256("b".repeat(64))
                .build();
    }
}

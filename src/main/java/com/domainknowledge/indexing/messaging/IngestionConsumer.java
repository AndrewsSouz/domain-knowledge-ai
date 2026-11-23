package com.domainknowledge.indexing.messaging;

import com.domainknowledge.indexing.service.DocumentIndexingService;
import com.domainknowledge.ingestion.model.DocumentUploadedEvent;
import com.domainknowledge.shared.util.LogContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Só recebe o evento e delega; não toca no banco. Grupo {@code indexing} (plan §7). */
@Slf4j
@Component
@RequiredArgsConstructor
class IngestionConsumer {

    static final String GROUP_ID = "indexing";

    private final DocumentIndexingService indexingService;

    @KafkaListener(topics = DocumentUploadedEvent.TOPIC, groupId = GROUP_ID)
    void onDocumentUploaded(DocumentUploadedEvent event) {
        try (var logContext = LogContext.documentId(event.documentId())) {
            log.info("Evento {} recebido", event.eventId());
            indexingService.index(event);
        }
    }
}

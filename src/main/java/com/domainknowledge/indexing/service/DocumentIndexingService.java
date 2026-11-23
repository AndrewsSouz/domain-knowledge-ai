package com.domainknowledge.indexing.service;

import com.domainknowledge.document.model.Document;
import com.domainknowledge.document.service.DocumentLifecycle;
import com.domainknowledge.document.service.DocumentQueryService;
import com.domainknowledge.ingestion.model.DocumentUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Leva um documento da fila até {@code INDEXED} (plan §8). Não trata erro:
 * qualquer exceção sobe pro error handler do Kafka, que decide entre tentar de
 * novo e marcar {@code FAILED} (plan §7). Assim o {@code FAILED} é decidido num
 * lugar só.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIndexingService {

    private final DocumentQueryService documents;
    private final DocumentLifecycle lifecycle;
    private final DocumentIndexer indexer;

    public void index(DocumentUploadedEvent event) {
        Document document = documents.getById(event.documentId());
        if (document.getVersion() != event.version()) {
            // Evento de outra versão do documento (fatia 4). Na fatia 1 não acontece.
            log.warn("Evento da versão {} ignorado: o documento está na versão {}",
                    event.version(), document.getVersion());
            return;
        }
        // Só assume se estiver em RECEIVED, QUEUED ou PROCESSING (RF6): INDEXED e
        // FAILED ficam como estão; PROCESSING é o caso da aplicação que caiu no meio.
        if (!lifecycle.claimForProcessing(document.getId())) {
            log.info("Evento ignorado: o documento já está {}", document.getStatus());
            return;
        }
        indexer.index(document);
        if (lifecycle.markIndexed(document.getId())) {
            log.info("Documento indexado");
        }
    }
}

package com.domainknowledge.ingestion.service;

import static com.domainknowledge.document.model.DocumentStatus.FAILED;
import static com.domainknowledge.document.model.DocumentStatus.RECEIVED;

import com.domainknowledge.document.exception.DuplicateDocumentException;
import com.domainknowledge.document.model.Document;
import com.domainknowledge.document.model.DocumentStatus;
import com.domainknowledge.document.model.NewDocument;
import com.domainknowledge.document.service.DocumentLifecycle;
import com.domainknowledge.document.service.DocumentQueryService;
import com.domainknowledge.infrastructure.storage.model.ObjectUpload;
import com.domainknowledge.infrastructure.storage.service.ObjectStorage;
import com.domainknowledge.ingestion.exception.EmptyFileException;
import com.domainknowledge.ingestion.exception.EventPublishException;
import com.domainknowledge.ingestion.exception.UnsupportedDocumentTypeException;
import com.domainknowledge.ingestion.messaging.IngestionProducer;
import com.domainknowledge.ingestion.model.DocumentType;
import com.domainknowledge.ingestion.model.DocumentUploadedEvent;
import com.domainknowledge.ingestion.model.UploadRequest;
import com.domainknowledge.ingestion.model.UploadResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Orquestra o upload (plan §6): valida, calcula o hash, trata duplicado,
 * grava no storage, registra e publica.
 *
 * <p>Ordem storage → banco → Kafka, de propósito, e sem uma transação em volta
 * (não dá pra ter transação entre S3, Postgres e Kafka). Se cair entre o
 * storage e o banco, sobra um arquivo órfão, inofensivo. Se cair entre o banco
 * e o Kafka, sobra um {@code RECEIVED}, visível e recuperável reenviando o
 * arquivo. Evento sem arquivo nunca acontece.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUploadService {

    /** Duplicado nesses estados volta pra fila (RF2): a publicação ou a indexação falhou. */
    private static final Set<DocumentStatus> REQUEUEABLE = Set.of(RECEIVED, FAILED);

    private final ContentTypeDetector contentTypeDetector;
    private final ObjectStorage storage;
    private final DocumentLifecycle lifecycle;
    private final DocumentQueryService documents;
    private final IngestionProducer producer;
    private final Clock clock;

    /**
     * @throws EmptyFileException                arquivo sem conteúdo
     * @throws UnsupportedDocumentTypeException  nem PDF nem DOCX
     * @throws EventPublishException             o Kafka não confirmou; o documento fica {@code RECEIVED}
     */
    public UploadResult upload(UploadRequest request) {
        String filename = request.originalFilename();
        // Vazio primeiro: sem bytes, o detector decidiria só pelo nome.
        if (request.sizeBytes() == 0) {
            throw new EmptyFileException(filename);
        }
        DocumentType type = detectType(request);
        String sha256 = hash(request);

        Optional<Document> existing = documents.findBySha256(sha256);
        if (existing.isPresent()) {
            return handleDuplicate(existing.get());
        }

        UUID id = UUID.randomUUID();
        String storageKey = "documents/%s/v1/original.%s".formatted(id, type.getExtension());
        store(request, storageKey, type, sha256);

        Document document;
        try {
            document = lifecycle.register(NewDocument.builder()
                    .id(id)
                    .title(StringUtils.hasText(request.title()) ? request.title().strip() : filename)
                    .originalFilename(filename)
                    .contentType(type.getMediaType())
                    .sizeBytes(request.sizeBytes())
                    .sha256(sha256)
                    .storageKey(storageKey)
                    .build());
        } catch (DuplicateDocumentException e) {
            // Outra requisição com o mesmo arquivo registrou primeiro. O objeto
            // que gravamos acima fica órfão (inofensivo).
            log.info("Upload concorrente do mesmo arquivo; usando o documento já registrado");
            return handleDuplicate(documents.findBySha256(sha256).orElseThrow());
        }
        return enqueue(document);
    }

    private UploadResult handleDuplicate(Document existing) {
        if (!REQUEUEABLE.contains(existing.getStatus())) {
            log.info("Documento {} já existe em {}; nada publicado", existing.getId(), existing.getStatus());
            return new UploadResult.AlreadyExists(existing);
        }
        if (!lifecycle.resetForRetry(existing.getId())) {
            // Outra requisição reenfileirou entre a leitura e agora.
            return new UploadResult.AlreadyExists(reload(existing.getId()));
        }
        log.info("Documento {} em {} reenviado; voltando pra fila", existing.getId(), existing.getStatus());
        return enqueue(reload(existing.getId()));
    }

    private UploadResult enqueue(Document document) {
        producer.publish(DocumentUploadedEvent.of(document, clock.instant()));
        // false = o consumidor já pegou o documento; não volta pra trás.
        lifecycle.markQueued(document.getId());
        return new UploadResult.Accepted(reload(document.getId()));
    }

    private DocumentType detectType(UploadRequest request) {
        return readContent(request, content -> contentTypeDetector.detect(content, request.originalFilename()))
                .orElseThrow(() -> new UnsupportedDocumentTypeException(request.originalFilename()));
    }

    private static String hash(UploadRequest request) {
        return readContent(request, ContentHasher::sha256);
    }

    private void store(UploadRequest request, String storageKey, DocumentType type, String sha256) {
        ObjectUpload upload = ObjectUpload.builder()
                .key(storageKey)
                .contentType(type.getMediaType())
                .sizeBytes(request.sizeBytes())
                .sha256(sha256)
                .build();
        readContent(request, content -> {
            storage.put(upload, content);
            return null;
        });
    }

    /** Abre um stream novo do conteúdo, entrega pra {@code reader} e fecha. */
    private static <T> T readContent(UploadRequest request, ContentReader<T> reader) {
        try (InputStream content = request.content().getInputStream()) {
            return reader.read(content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @FunctionalInterface
    private interface ContentReader<T> {
        T read(InputStream content) throws IOException;
    }

    private Document reload(UUID id) {
        return documents.getById(id);
    }
}

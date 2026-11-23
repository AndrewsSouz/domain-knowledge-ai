package com.domainknowledge.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.domainknowledge.document.model.Document;
import com.domainknowledge.document.model.DocumentStatus;
import com.domainknowledge.document.model.NewDocument;
import com.domainknowledge.document.repository.DocumentRepository;
import com.domainknowledge.document.service.DocumentLifecycle;
import com.domainknowledge.infrastructure.storage.model.ObjectUpload;
import com.domainknowledge.infrastructure.storage.service.ObjectStorage;
import com.domainknowledge.ingestion.messaging.IngestionProducer;
import com.domainknowledge.ingestion.model.DocumentUploadedEvent;
import com.domainknowledge.ingestion.model.UploadRequest;
import com.domainknowledge.ingestion.service.DocumentUploadService;
import com.domainknowledge.shared.util.ContentHasher;
import com.domainknowledge.support.IntegrationTest;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.KafkaAdmin;

/** O consumidor leva o documento até o fim do ciclo de vida (critério A2). */
@IntegrationTest
class DocumentIndexingIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    DocumentUploadService uploadService;

    @Autowired
    DocumentRepository repository;

    @Autowired
    DocumentLifecycle lifecycle;

    @Autowired
    ObjectStorage storage;

    @Autowired
    IngestionProducer producer;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @BeforeEach
    void cleanTable() {
        repository.deleteAllInBatch();
    }

    /** A2: upload → em até 10 s o documento está {@code INDEXED}. */
    @Test
    void uploadedDocumentIsIndexed() throws IOException {
        ClassPathResource pdf = new ClassPathResource("fixtures/sample.pdf");

        UUID id = uploadService.upload(UploadRequest.builder()
                .originalFilename("comprehensive-rules.pdf")
                .sizeBytes(pdf.contentLength())
                .content(pdf)
                .build()).document().getId();

        awaitStatus(id, DocumentStatus.INDEXED);
        assertThat(repository.findById(id).orElseThrow().getErrorMessage()).isNull();
    }

    /**
     * Uma mensagem que não é JSON válido não trava a partição: a mensagem
     * seguinte, na mesma partição (mesma chave), é processada.
     */
    @Test
    void malformedMessageDoesNotBlockThePartition() throws IOException {
        UUID id = UUID.randomUUID();
        try (var raw = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaAdmin.getConfigurationProperties().get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)),
                new StringSerializer(), new StringSerializer())) {
            raw.send(new ProducerRecord<>(DocumentUploadedEvent.TOPIC, id.toString(), "{isto não é json")).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        Document document = storedAndRegistered(id);
        producer.publish(DocumentUploadedEvent.of(document, Instant.now()));

        awaitStatus(id, DocumentStatus.INDEXED);
    }

    /** Grava o sample.pdf no storage e registra o documento, como o upload faria. */
    private Document storedAndRegistered(UUID id) throws IOException {
        ClassPathResource pdf = new ClassPathResource("fixtures/sample.pdf");
        String sha256;
        try (InputStream in = pdf.getInputStream()) {
            sha256 = ContentHasher.sha256(in);
        }
        String key = "documents/" + id + "/v1/original.pdf";
        try (InputStream in = pdf.getInputStream()) {
            storage.put(ObjectUpload.builder()
                    .key(key).contentType("application/pdf").sizeBytes(pdf.contentLength()).sha256(sha256)
                    .build(), in);
        }
        return lifecycle.register(NewDocument.builder()
                .id(id).title("regras").originalFilename("regras.pdf").contentType("application/pdf")
                .sizeBytes(pdf.contentLength()).sha256(sha256).storageKey(key)
                .build());
    }

    private void awaitStatus(UUID id, DocumentStatus status) {
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(status));
    }
}

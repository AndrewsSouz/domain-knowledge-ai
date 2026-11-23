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
import java.sql.Timestamp;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;

/** O consumidor leva o documento até o fim do ciclo de vida (A2, A5, A6, RF6, RF7). */
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

    @Autowired
    JdbcTemplate jdbcTemplate;

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

        publish(register(id, "fixtures/sample.pdf"));

        awaitStatus(id, DocumentStatus.INDEXED);
    }

    /**
     * A6: o objeto sumiu do storage antes do consumo → {@code FAILED} com a
     * mensagem, sem retentativa. Com retentativa (1 s + 2 s + 4 s) levaria
     * mais de 7 s; sem ela, chega em bem menos.
     */
    @Test
    void missingObjectFailsWithoutRetrying() throws IOException {
        UUID id = UUID.randomUUID();
        publish(register(id, null));

        await().atMost(Duration.ofSeconds(4)).untilAsserted(() ->
                assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(DocumentStatus.FAILED));
        assertThat(repository.findById(id).orElseThrow().getErrorMessage())
                .startsWith("Objeto não encontrado no storage: documents/" + id);
    }

    /** RF7: o arquivo no storage não é o que subiu (hash diferente) → {@code FAILED}, sem retentativa. */
    @Test
    void checksumMismatchFailsWithoutRetrying() throws IOException {
        UUID id = UUID.randomUUID();
        // Registrado com o hash do sample.pdf, mas o objeto gravado é outro arquivo.
        Document document = register(id, "fixtures/sample.pdf");
        overwriteObject(document.getStorageKey(), "fixtures/fake.pdf");
        publish(document);

        await().atMost(Duration.ofSeconds(4)).untilAsserted(() ->
                assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(DocumentStatus.FAILED));
        assertThat(repository.findById(id).orElseThrow().getErrorMessage())
                .startsWith("SHA-256 diferente do registrado no upload");
    }

    /**
     * A5: evento de documento já {@code INDEXED} é ignorado. Nada muda, nem o
     * {@code updatedAt}, durante a janela em que o consumidor já teria
     * processado.
     */
    @Test
    void eventOfIndexedDocumentIsIgnored() throws IOException {
        UUID id = UUID.randomUUID();
        Document document = register(id, "fixtures/sample.pdf");
        Instant longAgo = Instant.parse("2026-01-01T00:00:00Z");
        jdbcTemplate.update("update document set status = 'INDEXED', updated_at = ? where id = ?",
                Timestamp.from(longAgo), id);

        publish(document);

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(4)).untilAsserted(() -> {
            Document current = repository.findById(id).orElseThrow();
            assertThat(current.getStatus()).isEqualTo(DocumentStatus.INDEXED);
            assertThat(current.getUpdatedAt()).isEqualTo(longAgo);
        });
    }

    /**
     * A5 e RF6: documento parado em {@code PROCESSING} (a aplicação caiu no
     * meio) é processado de novo quando o evento volta, e termina em
     * {@code INDEXED}.
     */
    @Test
    void eventOfDocumentStuckInProcessingIsProcessedAgain() throws IOException {
        UUID id = UUID.randomUUID();
        Document document = register(id, "fixtures/sample.pdf");
        jdbcTemplate.update("update document set status = 'PROCESSING' where id = ?", id);

        publish(document);

        awaitStatus(id, DocumentStatus.INDEXED);
    }

    /**
     * Registra um documento com o hash do sample.pdf, como o upload faria.
     *
     * @param objectFixture o que gravar no storage; {@code null} não grava nada
     */
    private Document register(UUID id, String objectFixture) throws IOException {
        ClassPathResource pdf = new ClassPathResource("fixtures/sample.pdf");
        String sha256;
        try (InputStream in = pdf.getInputStream()) {
            sha256 = ContentHasher.sha256(in);
        }
        String key = "documents/" + id + "/v1/original.pdf";
        if (objectFixture != null) {
            overwriteObject(key, objectFixture);
        }
        return lifecycle.register(NewDocument.builder()
                .id(id).title("regras").originalFilename("regras.pdf").contentType("application/pdf")
                .sizeBytes(pdf.contentLength()).sha256(sha256).storageKey(key)
                .build());
    }

    private void overwriteObject(String key, String fixture) throws IOException {
        ClassPathResource content = new ClassPathResource(fixture);
        try (InputStream in = content.getInputStream()) {
            storage.put(ObjectUpload.builder()
                    .key(key).contentType("application/pdf").sizeBytes(content.contentLength()).sha256("-")
                    .build(), in);
        }
    }

    private void publish(Document document) {
        producer.publish(DocumentUploadedEvent.of(document, Instant.now()));
    }

    private void awaitStatus(UUID id, DocumentStatus status) {
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(status));
    }
}

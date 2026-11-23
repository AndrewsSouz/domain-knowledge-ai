package com.domainknowledge.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.domainknowledge.document.model.Document;
import com.domainknowledge.document.model.DocumentStatus;
import com.domainknowledge.document.repository.DocumentRepository;
import com.domainknowledge.infrastructure.storage.config.StorageProperties;
import com.domainknowledge.ingestion.model.DocumentUploadedEvent;
import com.domainknowledge.ingestion.model.UploadRequest;
import com.domainknowledge.ingestion.model.UploadResult;
import com.domainknowledge.shared.util.ContentHasher;
import com.domainknowledge.support.IntegrationTest;
import com.domainknowledge.support.TopicReader;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import software.amazon.awssdk.services.s3.S3Client;

/** Upload com Postgres, RustFS e Kafka de verdade (critérios A1 e A3). */
@IntegrationTest
class DocumentUploadServiceIntegrationTest {

    @Autowired
    DocumentUploadService service;

    @Autowired
    DocumentRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    S3Client s3Client;

    @Autowired
    StorageProperties storageProperties;

    @Autowired
    KafkaAdmin kafkaAdmin;

    private String sampleSha256;

    @BeforeEach
    void setUp() throws IOException {
        // As fixtures têm hash fixo: sem limpar, o segundo teste acharia o
        // documento do primeiro como duplicado.
        repository.deleteAllInBatch();
        try (InputStream in = new ClassPathResource("fixtures/sample.pdf").getInputStream()) {
            sampleSha256 = ContentHasher.sha256(in);
        }
    }

    /**
     * A1: linha registrada, objeto com o mesmo SHA-256, evento com chave =
     * documentId. O consumidor está rodando, então o status pode já ter
     * passado de {@code QUEUED} quando o teste confere.
     */
    @Test
    void uploadStoresRegistersAndPublishes() throws IOException {
        UploadResult result = service.upload(samplePdf());

        assertThat(result).isInstanceOf(UploadResult.Accepted.class);
        Document document = repository.findById(result.document().getId()).orElseThrow();
        assertThat(document.getStatus())
                .isIn(DocumentStatus.QUEUED, DocumentStatus.PROCESSING, DocumentStatus.INDEXED);
        assertThat(document.getSha256()).isEqualTo(sampleSha256);

        var head = s3Client.headObject(request -> request
                .bucket(storageProperties.bucket())
                .key(document.getStorageKey()));
        assertThat(head.metadata()).containsEntry("sha256", sampleSha256);
        assertThat(head.contentLength()).isEqualTo(document.getSizeBytes());

        assertThat(TopicReader.recordsWithKey(
                kafkaAdmin, DocumentUploadedEvent.TOPIC, document.getId().toString(), 1)).hasSize(1);
    }

    /** A3: o mesmo arquivo duas vezes devolve o mesmo documento e publica um evento só. */
    @Test
    void sameFileTwiceKeepsOneDocumentAndOneEvent() throws IOException {
        UploadResult first = service.upload(samplePdf());
        UploadResult second = service.upload(samplePdf());

        assertThat(second).isInstanceOf(UploadResult.AlreadyExists.class);
        assertThat(second.document().getId()).isEqualTo(first.document().getId());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(TopicReader.recordsWithKey(
                kafkaAdmin, DocumentUploadedEvent.TOPIC, first.document().getId().toString(), 1)).hasSize(1);
    }

    /**
     * A9 e RF2: reenviar um documento {@code FAILED} limpa o erro, publica de
     * novo e o fluxo termina em {@code INDEXED}.
     */
    @Test
    void failedDocumentIsRequeuedWhenSentAgain() throws IOException {
        UploadResult first = service.upload(samplePdf());
        UUID id = first.document().getId();
        awaitStatus(id, DocumentStatus.INDEXED);
        jdbcTemplate.update("update document set status = 'FAILED', error_message = 'hash diferente' where id = ?", id);

        UploadResult second = service.upload(samplePdf());

        assertThat(second).isInstanceOf(UploadResult.Accepted.class);
        assertThat(second.document().getId()).isEqualTo(id);
        assertThat(second.document().getErrorMessage()).isNull();
        awaitStatus(id, DocumentStatus.INDEXED);
        assertThat(TopicReader.recordsWithKey(
                kafkaAdmin, DocumentUploadedEvent.TOPIC, first.document().getId().toString(), 2)).hasSize(2);
    }

    private void awaitStatus(UUID id, DocumentStatus status) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(status));
    }

    private static UploadRequest samplePdf() throws IOException {
        ClassPathResource resource = new ClassPathResource("fixtures/sample.pdf");
        return UploadRequest.builder()
                .originalFilename("comprehensive-rules.pdf")
                .sizeBytes(resource.contentLength())
                .content(resource)
                .build();
    }
}

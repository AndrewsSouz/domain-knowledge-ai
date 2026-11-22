package com.domainknowledge.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domainknowledge.support.IntegrationTest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Confere que a migration V1 e a entidade batem (o Hibernate valida o schema
 * na subida) e que o banco barra o mesmo hash duas vezes.
 */
@IntegrationTest
class DocumentRepositoryIntegrationTest {

    @Autowired
    DocumentRepository repository;

    @BeforeEach
    void cleanTable() {
        repository.deleteAllInBatch();
    }

    @Test
    void savesAndReadsBackANewDocument() {
        NewDocument data = newDocument(randomSha256());
        Instant now = Instant.parse("2026-11-22T10:15:30.123456789Z");

        repository.save(Document.received(data, now));

        Document saved = repository.findById(data.id()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.RECEIVED);
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getSha256()).isEqualTo(data.sha256());
        assertThat(saved.getStorageKey()).isEqualTo(data.storageKey());
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2026-11-22T10:15:30.123456Z"));
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void findsDocumentByHash() {
        String sha256 = randomSha256();
        repository.save(Document.received(newDocument(sha256), Instant.now()));

        assertThat(repository.findBySha256(sha256)).isPresent();
        assertThat(repository.findBySha256(randomSha256())).isEmpty();
    }

    @Test
    void rejectsTheSameHashTwice() {
        String sha256 = randomSha256();
        repository.saveAndFlush(Document.received(newDocument(sha256), Instant.now()));

        assertThatThrownBy(() -> repository.saveAndFlush(Document.received(newDocument(sha256), Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static NewDocument newDocument(String sha256) {
        UUID id = UUID.randomUUID();
        return NewDocument.builder()
                .id(id)
                .title("Política de férias")
                .originalFilename("politica-ferias.pdf")
                .contentType("application/pdf")
                .sizeBytes(1024)
                .sha256(sha256)
                .storageKey("documents/" + id + "/v1/original.pdf")
                .build();
    }

    private static String randomSha256() {
        byte[] bytes = new byte[32];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}

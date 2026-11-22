package com.domainknowledge.document.repository;

import static com.domainknowledge.document.DocumentFixtures.newDocument;
import static com.domainknowledge.document.DocumentFixtures.randomSha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domainknowledge.document.model.Document;
import com.domainknowledge.document.model.DocumentStatus;
import com.domainknowledge.document.model.NewDocument;
import com.domainknowledge.support.IntegrationTest;
import java.time.Instant;
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
        NewDocument data = newDocument();
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
}

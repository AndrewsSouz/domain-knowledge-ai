package com.domainknowledge.document.service;

import static com.domainknowledge.document.model.DocumentStatus.FAILED;
import static com.domainknowledge.document.model.DocumentStatus.INDEXED;
import static com.domainknowledge.document.model.DocumentStatus.PROCESSING;
import static com.domainknowledge.document.model.DocumentStatus.QUEUED;
import static com.domainknowledge.document.model.DocumentStatus.RECEIVED;
import static com.domainknowledge.document.DocumentFixtures.newDocument;
import static com.domainknowledge.document.DocumentFixtures.randomSha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domainknowledge.document.exception.DuplicateDocumentException;
import com.domainknowledge.document.model.Document;
import com.domainknowledge.document.model.DocumentStatus;
import com.domainknowledge.document.model.NewDocument;
import com.domainknowledge.document.repository.DocumentRepository;
import com.domainknowledge.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cada transição do plan §5 contra os cinco status de origem: nos aceitos ela
 * muda o status e o {@code updatedAt}; nos outros não muda nada.
 */
@IntegrationTest
class DocumentLifecycleIntegrationTest {

    private static final Instant LONG_AGO = Instant.parse("2026-01-01T00:00:00Z");
    private static final String PREVIOUS_ERROR = "falha anterior";

    @Autowired
    DocumentLifecycle lifecycle;

    @Autowired
    DocumentRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTable() {
        repository.deleteAllInBatch();
    }

    @Test
    void registersNewDocumentAsReceived() {
        NewDocument data = newDocument();

        Document registered = lifecycle.register(data);

        assertThat(registered.getStatus()).isEqualTo(RECEIVED);
        assertThat(repository.findById(data.id())).isPresent();
    }

    @Test
    void rejectsRegisteringTheSameHashTwice() {
        String sha256 = randomSha256();
        lifecycle.register(newDocument(sha256));

        assertThatThrownBy(() -> lifecycle.register(newDocument(sha256)))
                .isInstanceOf(DuplicateDocumentException.class)
                .extracting("sha256").isEqualTo(sha256);
        assertThat(repository.count()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0} → QUEUED")
    @EnumSource(DocumentStatus.class)
    void markQueued(DocumentStatus from) {
        assertTransition(from, Set.of(RECEIVED), QUEUED, lifecycle::markQueued);
    }

    @ParameterizedTest(name = "{0} → PROCESSING")
    @EnumSource(DocumentStatus.class)
    void claimForProcessing(DocumentStatus from) {
        assertTransition(from, Set.of(RECEIVED, QUEUED, PROCESSING), PROCESSING, lifecycle::claimForProcessing);
    }

    @ParameterizedTest(name = "{0} → INDEXED")
    @EnumSource(DocumentStatus.class)
    void markIndexed(DocumentStatus from) {
        assertTransition(from, Set.of(PROCESSING), INDEXED, lifecycle::markIndexed);
    }

    @ParameterizedTest(name = "{0} → FAILED")
    @EnumSource(DocumentStatus.class)
    void markFailed(DocumentStatus from) {
        Document after = assertTransition(from, Set.of(PROCESSING), FAILED,
                id -> lifecycle.markFailed(id, "hash diferente"));

        if (from == PROCESSING) {
            assertThat(after.getErrorMessage()).isEqualTo("hash diferente");
        }
    }

    @ParameterizedTest(name = "{0} → RECEIVED (retry)")
    @EnumSource(DocumentStatus.class)
    void resetForRetry(DocumentStatus from) {
        Document after = assertTransition(from, Set.of(RECEIVED, FAILED), RECEIVED, lifecycle::resetForRetry);

        if (from == FAILED) {
            assertThat(after.getErrorMessage()).isNull();
        }
    }

    @Test
    void transitionOnUnknownDocumentDoesNothing() {
        assertThat(lifecycle.markQueued(UUID.randomUUID())).isFalse();
    }

    @Test
    void onlyOneOfConcurrentFinalTransitionsWins() throws Exception {
        UUID id = documentIn(PROCESSING);
        int attempts = 10;
        CountDownLatch start = new CountDownLatch(1);

        List<Boolean> results;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = IntStream.range(0, attempts)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return i % 2 == 0 ? lifecycle.markIndexed(id) : lifecycle.markFailed(id, "erro " + i);
                    }))
                    .toList();
            start.countDown();
            results = futures.stream().map(DocumentLifecycleIntegrationTest::join).toList();
        }

        assertThat(results).containsOnlyOnce(true);
        assertThat(repository.findById(id).orElseThrow().getStatus()).isIn(INDEXED, FAILED);
    }

    /**
     * Põe um documento em {@code from}, aplica a transição e confere o
     * resultado. Devolve o documento relido do banco.
     */
    private Document assertTransition(DocumentStatus from, Set<DocumentStatus> allowed, DocumentStatus to,
            Predicate<UUID> transition) {
        UUID id = documentIn(from);
        boolean expected = allowed.contains(from);

        boolean applied = transition.test(id);

        Document after = repository.findById(id).orElseThrow();
        assertThat(applied).isEqualTo(expected);
        if (expected) {
            assertThat(after.getStatus()).isEqualTo(to);
            assertThat(after.getUpdatedAt()).isAfter(LONG_AGO);
        } else {
            assertThat(after.getStatus()).isEqualTo(from);
            assertThat(after.getUpdatedAt()).isEqualTo(LONG_AGO);
            assertThat(after.getErrorMessage()).isEqualTo(from == FAILED ? PREVIOUS_ERROR : null);
        }
        return after;
    }

    /** Documento já gravado em {@code status}, com {@code updatedAt} antigo. */
    private UUID documentIn(DocumentStatus status) {
        NewDocument data = newDocument();
        lifecycle.register(data);
        // Atalho de teste: escreve o status direto, sem passar pelas transições.
        jdbcTemplate.update("update document set status = ?, error_message = ?, updated_at = ? where id = ?",
                status.name(), status == FAILED ? PREVIOUS_ERROR : null, Timestamp.from(LONG_AGO), data.id());
        return data.id();
    }

    private static <T> T join(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

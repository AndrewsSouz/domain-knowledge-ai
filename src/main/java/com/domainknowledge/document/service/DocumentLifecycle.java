package com.domainknowledge.document.service;

import static com.domainknowledge.document.model.DocumentStatus.FAILED;
import static com.domainknowledge.document.model.DocumentStatus.INDEXED;
import static com.domainknowledge.document.model.DocumentStatus.PROCESSING;
import static com.domainknowledge.document.model.DocumentStatus.QUEUED;
import static com.domainknowledge.document.model.DocumentStatus.RECEIVED;

import com.domainknowledge.document.exception.DuplicateDocumentException;
import com.domainknowledge.document.model.Document;
import com.domainknowledge.document.model.DocumentStatus;
import com.domainknowledge.document.model.NewDocument;
import com.domainknowledge.document.repository.DocumentRepository;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Única porta para criar um documento e mudar o status dele (plan §5). Cada
 * transição é um {@code UPDATE} condicional: devolve {@code true} se valeu e
 * {@code false} se o documento não estava num estado de origem aceito (ou não
 * existe). Quem chama decide o que fazer com o {@code false}.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DocumentLifecycle {

    /** Nome que o Postgres dá ao {@code unique} de {@code sha256} na V1. */
    private static final String SHA256_UNIQUE_CONSTRAINT = "document_sha256_key";

    /**
     * {@code RECEIVED}: o consumidor pode ser mais rápido que o upload gravar
     * {@code QUEUED}. {@code PROCESSING}: a aplicação caiu no meio e o Kafka
     * reentregou a mensagem (RF6).
     */
    private static final Set<DocumentStatus> CLAIMABLE = Set.of(RECEIVED, QUEUED, PROCESSING);

    /** {@code RECEIVED}: a publicação falhou. {@code FAILED}: a indexação falhou (RF2). */
    private static final Set<DocumentStatus> RETRYABLE = Set.of(RECEIVED, FAILED);

    private final DocumentRepository repository;
    private final Clock clock;

    /**
     * Grava o documento em {@code RECEIVED}.
     *
     * @throws DuplicateDocumentException se já existe documento com o mesmo hash
     */
    public Document register(NewDocument data) {
        try {
            return repository.saveAndFlush(Document.received(data, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            if (violates(e, SHA256_UNIQUE_CONSTRAINT)) {
                throw new DuplicateDocumentException(data.sha256(), e);
            }
            throw e;
        }
    }

    /**
     * {@code RECEIVED → QUEUED}. Só de {@code RECEIVED}: se o consumidor já
     * pegou o documento, ele não volta pra trás.
     */
    public boolean markQueued(UUID id) {
        return transition(id, Set.of(RECEIVED), QUEUED, null);
    }

    /** {@code RECEIVED | QUEUED | PROCESSING → PROCESSING}. */
    public boolean claimForProcessing(UUID id) {
        return transition(id, CLAIMABLE, PROCESSING, null);
    }

    /** {@code PROCESSING → INDEXED}. */
    public boolean markIndexed(UUID id) {
        return transition(id, Set.of(PROCESSING), INDEXED, null);
    }

    /** {@code PROCESSING → FAILED}, guardando o motivo. */
    public boolean markFailed(UUID id, String errorMessage) {
        return transition(id, Set.of(PROCESSING), FAILED, errorMessage);
    }

    /** {@code RECEIVED | FAILED → RECEIVED}, limpando o erro, pra publicar de novo. */
    public boolean resetForRetry(UUID id) {
        return transition(id, RETRYABLE, RECEIVED, null);
    }

    private boolean transition(UUID id, Set<DocumentStatus> from, DocumentStatus to, String errorMessage) {
        boolean applied = repository.transition(id, from, to, errorMessage, clock.instant()) == 1;
        if (!applied) {
            log.debug("Transição para {} ignorada: documento {} fora de {}", to, id, from);
        }
        return applied;
    }

    private static boolean violates(DataIntegrityViolationException e, String constraint) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return constraint.equals(violation.getConstraintName());
            }
        }
        return false;
    }
}

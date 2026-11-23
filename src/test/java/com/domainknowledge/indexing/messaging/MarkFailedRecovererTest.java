package com.domainknowledge.indexing.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.domainknowledge.document.service.DocumentLifecycle;
import com.domainknowledge.indexing.exception.ChecksumMismatchException;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.serializer.DeserializationException;

class MarkFailedRecovererTest {

    private final DocumentLifecycle lifecycle = mock(DocumentLifecycle.class);
    private final MarkFailedRecoverer recoverer = new MarkFailedRecoverer(lifecycle);

    @Test
    void marksTheDocumentFailedWithTheListenerReason() {
        UUID id = UUID.randomUUID();
        var failure = new ListenerExecutionFailedException("falhou",
                new ChecksumMismatchException(id, "a".repeat(64), "b".repeat(64)));

        recoverer.accept(record(id.toString()), failure);

        verify(lifecycle).markFailed(id, "SHA-256 diferente do registrado no upload (esperado %s, encontrado %s)"
                .formatted("a".repeat(64), "b".repeat(64)));
    }

    @Test
    void malformedMessageIsOnlyLogged() {
        var failure = new DeserializationException("JSON inválido", new byte[0], false, new RuntimeException());

        recoverer.accept(record(UUID.randomUUID().toString()), failure);

        verifyNoInteractions(lifecycle);
    }

    @Test
    void messageWithoutDocumentIdKeyIsOnlyLogged() {
        recoverer.accept(record("nao-e-uuid"), new IllegalStateException("x"));

        verifyNoInteractions(lifecycle);
    }

    @Test
    void longReasonIsTruncated() {
        String reason = MarkFailedRecoverer.reasonOf(new IllegalStateException("x".repeat(5000)));

        assertThat(reason).hasSize(MarkFailedRecoverer.MAX_MESSAGE_LENGTH);
    }

    @Test
    void reasonWithoutMessageUsesTheExceptionName() {
        assertThat(MarkFailedRecoverer.reasonOf(new IllegalStateException())).isEqualTo("IllegalStateException");
    }

    private static ConsumerRecord<String, String> record(String key) {
        return new ConsumerRecord<>("document-uploaded", 0, 42L, key, "{}");
    }
}

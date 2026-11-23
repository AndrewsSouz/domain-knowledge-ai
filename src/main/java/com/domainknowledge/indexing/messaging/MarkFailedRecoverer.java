package com.domainknowledge.indexing.messaging;

import com.domainknowledge.document.service.DocumentLifecycle;
import com.domainknowledge.shared.util.LogContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.serializer.DeserializationException;

/**
 * Último passo de uma mensagem que falhou (RF7): erro não retentável, ou as
 * tentativas acabaram. Marca o documento {@code FAILED} com o motivo, e a
 * mensagem sai do caminho (o offset é confirmado; a partição segue).
 *
 * <p>Mensagem malformada não tem documento confiável: só vai pro log. A DLQ
 * (fila de mensagens mortas) fica pra fatia 4.
 */
@Slf4j
@RequiredArgsConstructor
public class MarkFailedRecoverer implements ConsumerRecordRecoverer {

    /** Cabe na tela de quem consulta o documento; o erro completo fica no log. */
    static final int MAX_MESSAGE_LENGTH = 1000;

    private final DocumentLifecycle lifecycle;

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        if (causedBy(exception, DeserializationException.class)) {
            log.error("Mensagem malformada descartada: {}-{}@{}",
                    record.topic(), record.partition(), record.offset(), exception);
            return;
        }
        UUID documentId;
        try {
            documentId = UUID.fromString(String.valueOf(record.key()));
        } catch (IllegalArgumentException e) {
            log.error("Mensagem sem documentId na chave descartada: {}-{}@{}",
                    record.topic(), record.partition(), record.offset(), exception);
            return;
        }
        String reason = reasonOf(exception);
        // O recoverer roda fora do listener: o documentId volta pro MDC aqui.
        try (var logContext = LogContext.documentId(documentId)) {
            if (lifecycle.markFailed(documentId, reason)) {
                log.warn("Documento marcado FAILED: {}", reason, exception);
            } else {
                log.warn("Falha sem mudar o status (o documento não estava em PROCESSING): {}",
                        reason, exception);
            }
        }
    }

    /** A exceção que o listener lançou, sem o embrulho do Spring Kafka. */
    static String reasonOf(Exception exception) {
        Throwable cause = exception instanceof ListenerExecutionFailedException && exception.getCause() != null
                ? exception.getCause()
                : exception;
        String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        return message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
    }

    private static boolean causedBy(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}

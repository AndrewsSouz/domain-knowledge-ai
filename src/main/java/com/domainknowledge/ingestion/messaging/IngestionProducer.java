package com.domainknowledge.ingestion.messaging;

import com.domainknowledge.ingestion.exception.EventPublishException;
import com.domainknowledge.ingestion.model.DocumentUploadedEvent;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Só publica o {@link DocumentUploadedEvent}; não toca no banco. Espera a
 * confirmação do Kafka (o "ack") antes de voltar: o upload só marca
 * {@code QUEUED} com o evento gravado de verdade no broker.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionProducer {

    /**
     * Teto da espera. O próprio cliente desiste antes: {@code max.block.ms}
     * (5 s pra achar o broker) e {@code delivery.timeout.ms} (5 s pra
     * confirmar), no {@code application.yml}. Isto é só a rede de segurança.
     */
    static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(12);

    private final KafkaTemplate<String, DocumentUploadedEvent> kafkaTemplate;

    /** @throws EventPublishException se o Kafka não confirmar */
    public void publish(DocumentUploadedEvent event) {
        try {
            RecordMetadata metadata = kafkaTemplate
                    .send(DocumentUploadedEvent.TOPIC, event.documentId().toString(), event)
                    .get(PUBLISH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .getRecordMetadata();
            log.info("Evento {} publicado na partição {}, offset {}",
                    event.eventId(), metadata.partition(), metadata.offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException(event.documentId(), e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new EventPublishException(event.documentId(), e);
        }
    }
}

package com.domainknowledge.ingestion.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domainknowledge.ingestion.exception.EventPublishException;
import com.domainknowledge.ingestion.model.DocumentUploadedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

/** Kafka inalcançável vira {@link EventPublishException} rápido, sem travar. */
class IngestionProducerTest {

    @Test
    void failsFastWhenKafkaIsUnreachable() {
        // Porta 1: nada escuta ali. Timeouts curtos pro teste não demorar.
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 500,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 1000);
        var factory = new DefaultKafkaProducerFactory<String, DocumentUploadedEvent>(
                config, new StringSerializer(), new JacksonJsonSerializer<>());
        var producer = new IngestionProducer(new KafkaTemplate<>(factory));
        UUID documentId = UUID.randomUUID();
        DocumentUploadedEvent event = DocumentUploadedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(DocumentUploadedEvent.EVENT_TYPE)
                .occurredAt(Instant.now())
                .documentId(documentId)
                .build();

        Instant start = Instant.now();
        try {
            assertThatThrownBy(() -> producer.publish(event))
                    .isInstanceOf(EventPublishException.class)
                    .extracting("documentId").isEqualTo(documentId);
        } finally {
            factory.destroy();
        }
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(5));
    }
}

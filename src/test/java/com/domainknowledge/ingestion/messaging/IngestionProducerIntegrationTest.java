package com.domainknowledge.ingestion.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.domainknowledge.ingestion.model.DocumentUploadedEvent;
import com.domainknowledge.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Evento publicado no tópico certo, com a chave certa e o JSON do contrato. */
@IntegrationTest
class IngestionProducerIntegrationTest {

    @Autowired
    IngestionProducer producer;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Test
    void topicIsCreatedWithThreePartitions() throws Exception {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            var topic = admin.describeTopics(List.of(DocumentUploadedEvent.TOPIC))
                    .allTopicNames().get().get(DocumentUploadedEvent.TOPIC);
            assertThat(topic.partitions()).hasSize(3);
        }
    }

    @Test
    void publishesTheEventKeyedByDocumentId() {
        DocumentUploadedEvent event = event();

        producer.publish(event);

        ConsumerRecord<String, String> record = consumeByKey(event.documentId().toString());
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();

        JsonNode json = JsonMapper.builder().build().readTree(record.value());
        assertThat(json.get("eventId").asString()).isEqualTo(event.eventId().toString());
        assertThat(json.get("eventType").asString()).isEqualTo("DOCUMENT_UPLOADED");
        assertThat(json.get("occurredAt").asString()).isEqualTo("2026-11-22T14:03:00Z");
        assertThat(json.get("documentId").asString()).isEqualTo(event.documentId().toString());
        assertThat(json.get("version").asInt()).isEqualTo(1);
        assertThat(json.get("storageKey").asString()).isEqualTo(event.storageKey());
        assertThat(json.get("contentType").asString()).isEqualTo("application/pdf");
        assertThat(json.get("sha256").asString()).isEqualTo(event.sha256());
    }

    private ConsumerRecord<String, String> consumeByKey(String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaAdmin.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(DocumentUploadedEvent.TOPIC));
            Instant deadline = Instant.now().plusSeconds(15);
            while (Instant.now().isBefore(deadline)) {
                var found = StreamSupport.stream(consumer.poll(Duration.ofMillis(500)).spliterator(), false)
                        .filter(record -> key.equals(record.key()))
                        .findFirst();
                if (found.isPresent()) {
                    return found.get();
                }
            }
        }
        throw new AssertionError("Evento com a chave " + key + " não chegou no tópico");
    }

    private static DocumentUploadedEvent event() {
        UUID documentId = UUID.randomUUID();
        return DocumentUploadedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(DocumentUploadedEvent.EVENT_TYPE)
                .occurredAt(Instant.parse("2026-11-22T14:03:00Z"))
                .documentId(documentId)
                .version(1)
                .storageKey("documents/" + documentId + "/v1/original.pdf")
                .contentType("application/pdf")
                .sha256("b".repeat(64))
                .build();
    }
}

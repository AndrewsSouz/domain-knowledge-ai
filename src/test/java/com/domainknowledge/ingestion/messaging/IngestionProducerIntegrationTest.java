package com.domainknowledge.ingestion.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.domainknowledge.ingestion.model.DocumentUploadedEvent;
import com.domainknowledge.support.IntegrationTest;
import com.domainknowledge.support.TopicReader;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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

        List<ConsumerRecord<String, String>> records =
                TopicReader.recordsWithKey(kafkaAdmin, DocumentUploadedEvent.TOPIC, event.documentId().toString(), 1);
        assertThat(records).hasSize(1);
        ConsumerRecord<String, String> record = records.getFirst();
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

package com.domainknowledge.support;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Lê um tópico do Kafka de teste do começo, com um grupo novo a cada leitura,
 * e devolve as mensagens de uma chave. Valor como texto, pra o teste conferir
 * o JSON que foi de fato pro tópico.
 */
public final class TopicReader {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    /** Depois de achar o esperado, continua lendo um pouco pra pegar mensagem a mais. */
    private static final Duration SETTLE = Duration.ofSeconds(2);

    private TopicReader() {
    }

    public static List<ConsumerRecord<String, String>> recordsWithKey(
            KafkaAdmin kafkaAdmin, String topic, String key, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaAdmin.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plus(TIMEOUT);
            while (Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofMillis(300)).forEach(record -> {
                    if (key.equals(record.key())) {
                        found.add(record);
                    }
                });
                if (found.size() >= expected) {
                    deadline = min(deadline, Instant.now().plus(SETTLE));
                }
            }
        }
        return found;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}

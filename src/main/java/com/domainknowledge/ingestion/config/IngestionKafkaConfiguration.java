package com.domainknowledge.ingestion.config;

import com.domainknowledge.ingestion.model.DocumentUploadedEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
class IngestionKafkaConfiguration {

    /**
     * O {@code KafkaAdmin} do Boot cria o tópico na subida se ele não existir.
     * 3 partições: até 3 consumidores indexando em paralelo, cada documento
     * sempre na mesma partição (chave = {@code documentId}). Réplica 1 porque
     * o ambiente local tem um broker só.
     */
    @Bean
    NewTopic documentUploadedTopic() {
        return TopicBuilder.name(DocumentUploadedEvent.TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

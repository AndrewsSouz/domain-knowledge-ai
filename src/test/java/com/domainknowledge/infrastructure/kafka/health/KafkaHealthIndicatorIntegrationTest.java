package com.domainknowledge.infrastructure.kafka.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.domainknowledge.support.IntegrationTest;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

/** O Kafka aparece no health e fica {@code DOWN} quando não responde. */
@IntegrationTest
class KafkaHealthIndicatorIntegrationTest {

    @Autowired
    HealthEndpoint healthEndpoint;

    @Test
    void kafkaIsUpInTheHealthEndpoint() {
        assertThat(healthEndpoint.healthForPath("kafka").getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void kafkaIsDownWhenTheClusterDoesNotAnswer() {
        try (AdminClient unreachable = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 1_000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 1_000))) {

            Health health = new KafkaHealthIndicator(unreachable).health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }
    }
}

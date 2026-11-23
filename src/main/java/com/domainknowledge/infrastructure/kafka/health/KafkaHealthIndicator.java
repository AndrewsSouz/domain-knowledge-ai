package com.domainknowledge.infrastructure.kafka.health;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

/**
 * Entra no {@code /actuator/health} como {@code kafka} (RNF4). O Spring Boot 4
 * não traz um indicador de Kafka pronto. Pergunta ao cluster quem são os
 * brokers: se responde em 3 s, está de pé. Qualquer exceção vira {@code DOWN}.
 */
@Component
@RequiredArgsConstructor
class KafkaHealthIndicator extends AbstractHealthIndicator {

    private static final int TIMEOUT_MS = 3_000;

    private final AdminClient adminClient;

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        DescribeClusterResult cluster = adminClient.describeCluster(new DescribeClusterOptions().timeoutMs(TIMEOUT_MS));
        builder.up()
                .withDetail("clusterId", cluster.clusterId().get(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .withDetail("brokers", cluster.nodes().get(TIMEOUT_MS, TimeUnit.MILLISECONDS).size());
    }
}
